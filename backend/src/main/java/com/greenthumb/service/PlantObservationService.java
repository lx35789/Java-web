package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.entity.PlantObservationEntity;
import com.greenthumb.entity.PlantPhotoEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.PlantObservationMapper;
import com.greenthumb.mapper.PlantPhotoMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.vo.ObservationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 植物状态观测 —— 养护闭环的「反馈」那一半。
 *
 * <p>care_log 记录「人做了什么」，本服务记录「植物变成什么样」。只有后者存在，
 * 养护计划才可能从开环变闭环：叶子连续发蔫 → 缩短浇水间隔 → 用户少浇一次 →
 * 指数回升。没有这条链路，"个性化养护"就只是把基准间隔乘几个固定系数。
 *
 * <p>输入设计成三个选择题（叶片/长势/黄叶），十几秒能答完；照片来源的观测
 * 会额外带 AI 算出的色彩占比，两者加权合成 0-100 的 health_index 供画曲线。
 */
@Service
public class PlantObservationService {

    private static final Logger log = LoggerFactory.getLogger(PlantObservationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PlantObservationMapper observationMapper;
    private final UserPlantMapper plantMapper;
    private final PlantPhotoMapper photoMapper;
    private final PhotoService photoService;
    private final AiClientService aiClient;
    private final CarePlanService planService;

    public PlantObservationService(PlantObservationMapper observationMapper,
                                   UserPlantMapper plantMapper,
                                   PlantPhotoMapper photoMapper,
                                   PhotoService photoService,
                                   AiClientService aiClient,
                                   CarePlanService planService) {
        this.observationMapper = observationMapper;
        this.plantMapper = plantMapper;
        this.photoMapper = photoMapper;
        this.photoService = photoService;
        this.aiClient = aiClient;
        this.planService = planService;
    }

    /* ==================== 写入 ==================== */

    /**
     * 记录一次状态观测。
     *
     * @param body leafScore / growthScore / yellowLeaf 三个主观项（1~3 或 1~4 档），
     *             可选 photoId（带照片时自动补客观色彩指标）、note、observedAt
     */
    @Transactional
    public ObservationVO create(Long userId, Long userPlantId, Map<String, Object> body) {
        UserPlantEntity plant = owned(userId, userPlantId);

        Integer leaf = intOr(body.get("leafScore"), 2);
        Integer growth = intOr(body.get("growthScore"), 2);
        Integer yellow = intOr(body.get("yellowLeaf"), 1);
        if (leaf < 1 || leaf > 4) {
            throw new BizException(ErrorCode.PARAM_INVALID, "叶片状态取值应为 1~4");
        }
        if (growth < 1 || growth > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "长势取值应为 1~3");
        }
        if (yellow < 0 || yellow > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "黄叶数量取值应为 0~3");
        }

        PlantObservationEntity e = new PlantObservationEntity();
        e.setUserPlantId(userPlantId);
        e.setLeafScore(leaf);
        e.setGrowthScore(growth);
        e.setYellowLeaf(yellow);
        e.setNote(str(body.get("note")));
        e.setSource(body.get("photoId") == null ? "MANUAL" : "PHOTO");

        LocalDateTime observedAt = parseTime(body.get("observedAt"));
        e.setObservedAt(observedAt == null ? LocalDateTime.now() : observedAt);

        Long photoId = longOrNull(body.get("photoId"));
        if (photoId != null) {
            e.setPhotoId(photoId);
            fillPhotoMetrics(userId, photoId, e);
        }

        int subjective = subjectiveIndex(leaf, growth, yellow);
        Integer objective = objectiveIndex(e.getGreenRatio(), e.getYellowRatio(), e.getBrownRatio());
        int health = objective == null
                ? subjective
                : (int) Math.round(subjective * 0.65 + objective * 0.35);
        health = clamp(health, 0, 100);
        e.setHealthIndex(health);
        e.setCreatedAt(LocalDateTime.now());
        observationMapper.insert(e);

        // 同步植物的健康状态，让列表页/详情页不用每次现算
        String status = health >= 80 ? "HEALTHY" : (health >= 55 ? "WARNING" : "DISEASED");
        if (!Objects.equals(plant.getHealthStatus(), status)) {
            plant.setHealthStatus(status);
            plant.setUpdatedAt(LocalDateTime.now());
            plantMapper.updateById(plant);
        }

        // 反馈进计划：连续发蔫会缩短间隔，持续健康会放宽（原因写在 plan.llm_explain 里）
        planService.adapt(userId, userPlantId, "OBSERVATION");

        log.info("植物 {} 记录观测：健康指数 {}（主观 {}，客观 {}）", userPlantId, health, subjective, objective);
        return toVO(e);
    }

    /** 有照片就调 AI 算色彩占比；失败只记日志，不阻断，因为主观三项已经足够出分 */
    private void fillPhotoMetrics(Long userId, Long photoId, PlantObservationEntity e) {
        try {
            PlantPhotoEntity photo = photoMapper.selectById(photoId);
            if (photo == null || !Objects.equals(photo.getUserId(), userId)) {
                return;
            }
            byte[] bytes = photoService.readBytes(photo);
            AiClientService.PlantMetrics m = aiClient.plantMetrics(bytes, photoId + ".jpg");
            if (!m.available()) {
                log.info("照片 {} 色彩分析不可用：{}", photoId, m.reason());
                return;
            }
            e.setGreenRatio(ratio(m.greenRatio()));
            e.setYellowRatio(ratio(m.yellowRatio()));
            e.setBrownRatio(ratio(m.brownRatio()));
        } catch (Exception ex) {
            log.warn("照片 {} 色彩分析失败：{}", photoId, ex.getMessage());
        }
    }

    /* ==================== 读取 ==================== */

    /** 曲线数据：按时间正序的点 + 摘要 + 拐点 */
    public Map<String, Object> series(Long userId, Long userPlantId, int days) {
        owned(userId, userPlantId);
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(7, Math.min(days, 365)));

        List<PlantObservationEntity> rows = observationMapper.selectList(
                new LambdaQueryWrapper<PlantObservationEntity>()
                        .eq(PlantObservationEntity::getUserPlantId, userPlantId)
                        .ge(PlantObservationEntity::getObservedAt, from)
                        .orderByAsc(PlantObservationEntity::getObservedAt));

        List<Map<String, Object>> points = new ArrayList<>();
        List<Map<String, Object>> turns = new ArrayList<>();
        Integer prev = null;
        for (PlantObservationEntity r : rows) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", r.getId());
            p.put("date", r.getObservedAt() == null ? "" : r.getObservedAt().format(DATE));
            p.put("time", r.getObservedAt() == null ? "" : r.getObservedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            p.put("index", r.getHealthIndex());
            p.put("source", r.getSource());
            p.put("note", r.getNote());
            points.add(p);

            // 单次波动 ≥15 分视为拐点，前端在曲线上标出来
            if (prev != null && r.getHealthIndex() != null) {
                int d = r.getHealthIndex() - prev;
                if (Math.abs(d) >= 15) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("date", p.get("date"));
                    t.put("index", r.getHealthIndex());
                    t.put("delta", d);
                    t.put("reason", d < 0 ? "明显下滑" : "明显回升");
                    turns.add(t);
                }
            }
            if (r.getHealthIndex() != null) {
                prev = r.getHealthIndex();
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", points.size());
        summary.put("days", days);
        if (points.isEmpty()) {
            summary.put("latest", null);
            summary.put("avg", null);
            summary.put("trend", "NONE");
        } else {
            int latest = (int) points.get(points.size() - 1).get("index");
            int sum = 0;
            for (Map<String, Object> p : points) {
                sum += (int) p.get("index");
            }
            int avg = Math.round((float) sum / points.size());
            // 趋势：最近三次 vs 更早三次
            String trend = "FLAT";
            if (points.size() >= 2) {
                int head = (int) points.get(0).get("index");
                if (latest - head >= 8) {
                    trend = "UP";
                } else if (latest - head <= -8) {
                    trend = "DOWN";
                }
            }
            summary.put("latest", latest);
            summary.put("avg", avg);
            summary.put("trend", trend);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userPlantId", userPlantId);
        out.put("points", points);
        out.put("turns", turns);
        out.put("summary", summary);
        return out;
    }

    /** 最近一次观测，详情页头部用 */
    public ObservationVO latest(Long userId, Long userPlantId) {
        owned(userId, userPlantId);
        PlantObservationEntity e = observationMapper.selectOne(
                new LambdaQueryWrapper<PlantObservationEntity>()
                        .eq(PlantObservationEntity::getUserPlantId, userPlantId)
                        .orderByDesc(PlantObservationEntity::getObservedAt)
                        .last("limit 1"));
        return e == null ? null : toVO(e);
    }

    /** 供养护计划自适应读取：最近 N 条，按时间正序 */
    public List<PlantObservationEntity> recent(Long userPlantId, int limit) {
        List<PlantObservationEntity> rows = observationMapper.selectList(
                new LambdaQueryWrapper<PlantObservationEntity>()
                        .eq(PlantObservationEntity::getUserPlantId, userPlantId)
                        .orderByDesc(PlantObservationEntity::getObservedAt)
                        .last("limit " + Math.max(1, Math.min(limit, 50))));
        List<PlantObservationEntity> asc = new ArrayList<>(rows);
        java.util.Collections.reverse(asc);
        return asc;
    }

    /* ==================== 指数计算 ==================== */

    /**
     * 主观三项 → 0-100。
     *
     * <p>扣分权重来自 docs/13 的经验设定：叶片发蔫是最强的负面信号（-20/-36），
     * 黄叶按数量线性扣，长势只做小幅加减 —— 用户对"有没有新叶"的判断波动较大，
     * 给太大权重会让曲线噪声盖过真实趋势。
     */
    static int subjectiveIndex(int leaf, int growth, int yellow) {
        int s = 100;
        s -= switch (leaf) {
            case 1 -> 0;
            case 2 -> 8;
            case 3 -> 20;
            default -> 36;
        };
        s -= switch (yellow) {
            case 0 -> 0;
            case 1 -> 6;
            case 2 -> 15;
            default -> 28;
        };
        s += switch (growth) {
            case 1 -> 10;   // 有新叶
            case 3 -> -10;  // 有落叶
            default -> 0;
        };
        return clamp(s, 0, 100);
    }

    /**
     * 色彩占比 → 0-100。褐色权重最高（枯焦不可逆），黄色次之，绿色为正。
     * 前景像素太少（没拍到植株/糊了）返回 null，让调用方退化为纯主观分。
     */
    static Integer objectiveIndex(BigDecimal green, BigDecimal yellow, BigDecimal brown) {
        if (green == null || yellow == null || brown == null) {
            return null;
        }
        double g = green.doubleValue(), y = yellow.doubleValue(), b = brown.doubleValue();
        double total = g + y + b;
        if (total < 0.02) {
            return null;
        }
        double score = 100 * (g - 0.9 * y - 1.3 * b) / total;
        return clamp((int) Math.round(score), 0, 100);
    }

    /* ==================== 内部 ==================== */

    private UserPlantEntity owned(Long userId, Long userPlantId) {
        UserPlantEntity p = plantMapper.selectById(userPlantId);
        if (p == null || !Objects.equals(p.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
        }
        return p;
    }

    private ObservationVO toVO(PlantObservationEntity e) {
        return new ObservationVO(e.getId(), e.getObservedAt(), e.getSource(),
                e.getLeafScore(), e.getGrowthScore(), e.getYellowLeaf(),
                e.getGreenRatio(), e.getYellowRatio(), e.getBrownRatio(),
                e.getHealthIndex(), e.getNote());
    }

    private static BigDecimal ratio(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static Integer intOr(Object o, int def) {
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    private static Long longOrNull(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static LocalDateTime parseTime(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim().replace('T', ' ');
        try {
            if (s.length() <= 10) {
                return LocalDate.parse(s).atTime(LocalDateTime.now().toLocalTime().withNano(0));
            }
            if (s.length() == 16) {
                s = s + ":00";
            }
            return LocalDateTime.parse(s.replace(' ', 'T').substring(0, 19));
        } catch (Exception e) {
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
