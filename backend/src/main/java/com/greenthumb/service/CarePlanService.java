package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.entity.CareLogEntity;
import com.greenthumb.entity.CarePlanEntity;
import com.greenthumb.entity.CareTaskEntity;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.CareLogMapper;
import com.greenthumb.mapper.CarePlanMapper;
import com.greenthumb.mapper.CareTaskMapper;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.vo.CarePlanVO;
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
 * 养护计划：按系数算出间隔，再按间隔展开成日历任务。
 *
 * <p>核心规则来自 docs/02-项目重设计方案.md 3.4 节：
 * <pre>
 * 有效间隔 = clamp(基准间隔 × 季节 × 光照 × 温度 × 湿度 × 盆材质 × 空调房, 2, 30)
 * </pre>
 * 每个系数都记进 coefficient_detail，前端能完整展示"为什么是 5 天"。
 *
 * <p>设计上刻意**不引入大模型**：间隔计算是确定性规则，可复现、可解释、零成本。
 * 大模型只用于生成自然语言解释（可选），不参与数值决策。
 */
@Service
public class CarePlanService {

    private static final Logger log = LoggerFactory.getLogger(CarePlanService.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 浇水间隔的合理边界（天）：小于 2 天没必要，大于 30 天会漏浇 */
    private static final int MIN_WATER_DAYS = 2;
    private static final int MAX_WATER_DAYS = 30;
    /** 施肥间隔边界 */
    private static final int MIN_FERT_DAYS = 14;
    private static final int MAX_FERT_DAYS = 90;
    /** 一次生成多少个未来任务（日历覆盖约一个月） */
    private static final int HORIZON_TASKS = 4;

    private final CarePlanMapper planMapper;
    private final CareTaskMapper taskMapper;
    private final CareLogMapper logMapper;
    private final UserPlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;
    private final com.greenthumb.mapper.PlantObservationMapper observationMapper;

    public CarePlanService(CarePlanMapper planMapper,
                           CareTaskMapper taskMapper,
                           CareLogMapper logMapper,
                           UserPlantMapper plantMapper,
                           PlantSpeciesMapper speciesMapper,
                           com.greenthumb.mapper.PlantObservationMapper observationMapper) {
        this.planMapper = planMapper;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.plantMapper = plantMapper;
        this.speciesMapper = speciesMapper;
        this.observationMapper = observationMapper;
    }

    /* ==================== 查询 ==================== */

    public List<CarePlanVO> list(Long userId, Long userPlantId) {
        LambdaQueryWrapper<CarePlanEntity> w = new LambdaQueryWrapper<CarePlanEntity>()
                .eq(CarePlanEntity::getUserId, userId)
                .orderByAsc(CarePlanEntity::getUserPlantId)
                .orderByAsc(CarePlanEntity::getPlanType);
        if (userPlantId != null) {
            w.eq(CarePlanEntity::getUserPlantId, userPlantId);
        }
        List<CarePlanEntity> rows = planMapper.selectList(w);
        List<CarePlanVO> out = new ArrayList<>(rows.size());
        for (CarePlanEntity p : rows) {
            out.add(toVO(p));
        }
        return out;
    }

    public CarePlanVO get(Long userId, Long id) {
        return toVO(owned(userId, id));
    }

    /* ==================== 生成 ==================== */

    /**
     * 诊断背景：把「这次诊断出什么问题」带进养护计划。
     *
     * @param label       病灶名（如「绿萝·叶斑病」，已含类别提示）
     * @param waterFactor 浇水间隔修正系数；真菌/细菌病害期间适当拉长（>1）避免高湿助长病斑
     * @param note       写给用户看的一句话提醒，落到 plan.llm_explain
     */
    public record PlanContext(String label, Double waterFactor, String note) {
    }

    /**
     * 为一盆植物生成全部养护计划（浇水 + 施肥）。
     *
     * <p>幂等设计：重复调用不会产生重复计划 —— 先把该植物旧的启用中计划停用
     * （is_active=0），再插入新的。历史任务保留，不删。
     */
    @Transactional
    public List<CarePlanVO> generate(Long userId, Long userPlantId) {
        return generate(userId, userPlantId, null);
    }

    /**
     * 带诊断背景生成：浇水间隔会额外乘一个病害系数，并把这层原因写进 llm_explain，
     * 这样前端「为什么是 15 天」能直接读出来，而不是一个凭空变大的数字。
     */
    @Transactional
    public List<CarePlanVO> generate(Long userId, Long userPlantId, PlanContext ctx) {
        UserPlantEntity plant = plantMapper.selectById(userPlantId);
        if (plant == null || !Objects.equals(plant.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
        }
        PlantSpeciesEntity sp = plant.getSpeciesId() == null
                ? null : speciesMapper.selectById(plant.getSpeciesId());
        if (sp == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "这盆植物没有关联物种，无法生成养护计划");
        }

        // 1) 停用旧计划
        List<CarePlanEntity> old = planMapper.selectList(new LambdaQueryWrapper<CarePlanEntity>()
                .eq(CarePlanEntity::getUserId, userId)
                .eq(CarePlanEntity::getUserPlantId, userPlantId)
                .eq(CarePlanEntity::getIsActive, 1));
        for (CarePlanEntity p : old) {
            p.setIsActive(0);
            p.setEndDate(LocalDate.now().minusDays(1));
            p.setUpdatedAt(LocalDateTime.now());
            planMapper.updateById(p);
            // 同步清掉这些计划下还没做的任务，避免日历上出现两份
            taskMapper.delete(new LambdaQueryWrapper<CareTaskEntity>()
                    .eq(CareTaskEntity::getPlanId, p.getId())
                    .eq(CareTaskEntity::getStatus, "PENDING"));
        }

        // 2) 生成浇水计划
        LocalDate today = LocalDate.now();
        Coefficients c = waterCoefficients(plant, sp, today);
        // 诊断背景参与系数计算：先写进 factors，后面 product() 会自动乘进去，
        // 「为什么间隔变长了」就能在同一份 coefficient_detail 里查到
        if (ctx != null && ctx.waterFactor() != null && !ctx.waterFactor().equals(1.0)) {
            c.factors.put("disease", round2(ctx.waterFactor()));
            c.labels.put("disease", ctx.label() == null ? "病害期" : ctx.label() + "·病害期");
        }
        int waterDays = clamp(
                Math.round(sp.getBaseWaterIntervalDays() == null ? 7 : sp.getBaseWaterIntervalDays()
                        * c.product()),
                MIN_WATER_DAYS, MAX_WATER_DAYS);
        CarePlanVO water = createPlan(userId, plant, sp, "WATER", waterDays, c,
                buildExplain("WATER", waterDays, c, ctx), today);

        // 3) 生成施肥计划
        List<CarePlanVO> out = new ArrayList<>();
        out.add(water);
        Integer fertBase = sp.getFertilizerIntervalDays();
        if (fertBase != null && fertBase > 0) {
            double fc = seasonFertilizerFactor(today);
            int fertDays = clamp(Math.round(fertBase * fc), MIN_FERT_DAYS, MAX_FERT_DAYS);
            Map<String, Double> cd = new LinkedHashMap<>();
            cd.put("season", round2(fc));
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("season", seasonLabel(today) + "施肥系数");
            out.add(createPlan(userId, plant, sp, "FERTILIZE", fertDays,
                    new Coefficients(cd, labels, fertBase),
                    buildExplain("FERTILIZE", fertDays, new Coefficients(cd, labels, fertBase), ctx), today));
        }
        log.info("为植物 {} 生成养护计划：浇水 {} 天，施肥 {} 项{}",
                userPlantId, waterDays, out.size() - 1, ctx == null ? "" : "（含诊断背景：" + ctx.label() + "）");
        return out;
    }

    /**
     * 生成一句人话解释：把系数、基准、结果串起来，有诊断背景时补上病灶与针对性动作。
     * 空计划也能读，前端不用为 null 做特殊处理。
     */
    private String buildExplain(String planType, int days, Coefficients c, PlanContext ctx) {
        StringBuilder sb = new StringBuilder();
        if ("WATER".equals(planType)) {
            if (!c.labels.isEmpty()) {
                sb.append(String.join("；", c.labels.values())).append("，");
            }
            sb.append("基准 ").append(c.base).append(" 天，算下来每 ").append(days).append(" 天浇一次。");
        } else {
            if (!c.labels.isEmpty()) {
                sb.append(String.join("；", c.labels.values())).append("，每 ");
            } else {
                sb.append("每 ");
            }
            sb.append(days).append(" 天施一次。");
        }
        if (ctx != null) {
            sb.append("本次诊断：").append(ctx.label() == null ? "有病状" : ctx.label()).append("。");
            if (ctx.note() != null && !ctx.note().isBlank()) {
                sb.append(ctx.note());
            }
            if ("WATER".equals(planType) && ctx.waterFactor() != null && ctx.waterFactor() > 1.0) {
                sb.append("病害未控制前先别往叶面喷水。");
            }
            if ("FERTILIZE".equals(planType)) {
                sb.append("病害未控制前建议暂停施肥，避免刺激新叶招致病菌。");
            }
        }
        return sb.toString();
    }

    private CarePlanVO createPlan(Long userId, UserPlantEntity plant, PlantSpeciesEntity sp,
                                  String planType, int days, Coefficients c, String explain, LocalDate today) {
        CarePlanEntity p = new CarePlanEntity();
        p.setUserId(userId);
        p.setUserPlantId(plant.getId());
        p.setPlanType(planType);
        p.setSeason(seasonCode(today));
        p.setBaseIntervalDays(c.base);
        p.setEffectiveIntervalDays(days);
        p.setCoefficientDetail(toJson(c.factors));
        p.setStartDate(today);
        p.setGeneratedBy("RULE");
        p.setLlmExplain(explain);
        p.setIsActive(1);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p.setDeleted(0);
        planMapper.insert(p);

        // 按间隔往后展开任务
        LocalDate due = today.plusDays(days);
        for (int i = 0; i < HORIZON_TASKS; i++) {
            CareTaskEntity t = new CareTaskEntity();
            t.setPlanId(p.getId());
            t.setUserId(userId);
            t.setUserPlantId(plant.getId());
            t.setTaskType(planType);
            t.setDueDate(due);
            t.setStatus("PENDING");
            t.setReminded(0);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            taskMapper.insert(t);
            due = due.plusDays(days);
        }

        CarePlanVO vo = toVO(p);
        // 系数明细与计算过程只对本次生成返回，便于前端立即展示"为什么"
        return vo;
    }

    /* ==================== 自适应（闭环） ==================== */

    /**
     * 按实际记录校准浇水间隔 —— 把计划从「一次性算出来的静态值」变成「跟着这盆植物走」。
     *
     * <p>两类证据：
     * <ul>
     *   <li><b>健康指数趋势</b>：最近一次 ≤55 或单次下滑 ≥12 → 缩短；连续三次 ≥85 → 放宽；</li>
     *   <li><b>实际浇水节奏</b>：用户总比计划早浇，说明偏干；总拖后，说明偏湿或不需要那么勤。</li>
     * </ul>
     *
     * <p>每次改动幅度限制在 0.85~1.15 的乘积内，且间隔变化至少 1 天，
     * 避免曲线抖一下就重排整个日历。改动原因写进 llm_explain，前端原样展示。
     *
     * @param trigger OBSERVATION / TASK_DONE / MANUAL，仅用于日志
     * @return 是否真的改了间隔
     */
    @Transactional
    public boolean adapt(Long userId, Long userPlantId, String trigger) {
        CarePlanEntity plan = planMapper.selectOne(new LambdaQueryWrapper<CarePlanEntity>()
                .eq(CarePlanEntity::getUserId, userId)
                .eq(CarePlanEntity::getUserPlantId, userPlantId)
                .eq(CarePlanEntity::getPlanType, "WATER")
                .eq(CarePlanEntity::getIsActive, 1)
                .orderByDesc(CarePlanEntity::getId)
                .last("limit 1"));
        if (plan == null || plan.getEffectiveIntervalDays() == null) {
            return false;
        }
        int current = plan.getEffectiveIntervalDays();
        LocalDate today = LocalDate.now();
        List<String> reasons = new ArrayList<>();
        double factor = 1.0;

        // ---- 证据 1：健康指数趋势 ----
        List<com.greenthumb.entity.PlantObservationEntity> obs = observationMapper.selectList(
                new LambdaQueryWrapper<com.greenthumb.entity.PlantObservationEntity>()
                        .eq(com.greenthumb.entity.PlantObservationEntity::getUserPlantId, userPlantId)
                        .orderByDesc(com.greenthumb.entity.PlantObservationEntity::getObservedAt)
                        .last("limit 4"));
        java.util.Collections.reverse(obs);
        if (obs.size() >= 2) {
            int last = obs.get(obs.size() - 1).getHealthIndex() == null ? 100 : obs.get(obs.size() - 1).getHealthIndex();
            int prev = obs.get(obs.size() - 2).getHealthIndex() == null ? 100 : obs.get(obs.size() - 2).getHealthIndex();
            if (last <= 55) {
                factor *= 0.9;
                reasons.add("最近一次健康指数只有 " + last);
            } else if (prev - last >= 12) {
                factor *= 0.92;
                reasons.add("健康指数从 " + prev + " 掉到 " + last);
            } else if (last >= 85 && prev >= 85 && obs.size() >= 3
                    && obs.get(obs.size() - 3).getHealthIndex() != null
                    && obs.get(obs.size() - 3).getHealthIndex() >= 85) {
                factor *= 1.08;
                reasons.add("最近三次观测都在 85 以上");
            }
        }

        // ---- 证据 2：实际浇水节奏（最近 3 个间隔）----
        List<CareLogEntity> logs = logMapper.selectList(new LambdaQueryWrapper<CareLogEntity>()
                .eq(CareLogEntity::getUserPlantId, userPlantId)
                .eq(CareLogEntity::getLogType, "WATER")
                .ge(CareLogEntity::getLoggedAt, today.minusDays(75).atStartOfDay())
                .orderByAsc(CareLogEntity::getLoggedAt));
        Double actualAvg = null;
        if (logs.size() >= 2) {
            int n = Math.min(3, logs.size() - 1);
            long sum = 0;
            int used = 0;
            for (int i = logs.size() - n; i < logs.size(); i++) {
                long d = java.time.Duration.between(
                        logs.get(i - 1).getLoggedAt(), logs.get(i).getLoggedAt()).toDays();
                if (d > 0) {
                    sum += d;
                    used++;
                }
            }
            if (used > 0) {
                actualAvg = (double) sum / used;
            }
        }
        if (actualAvg != null && actualAvg >= 1.5) {
            double ratio = actualAvg / current;
            if (ratio <= 0.75) {
                factor *= 0.9;
                reasons.add(String.format("实际每 %.1f 天浇一次，比计划 %.0f 天短", actualAvg, (double) current));
            } else if (ratio >= 1.35) {
                factor *= 1.1;
                reasons.add(String.format("实际每 %.1f 天浇一次，比计划 %.0f 天长", actualAvg, (double) current));
            }
        }

        if (reasons.isEmpty()) {
            return false;
        }

        int next = clamp(Math.round(current * factor), MIN_WATER_DAYS, MAX_WATER_DAYS);
        // 幅度不足 1 天就不动，避免"每天都在改计划"的观感
        if (next == current) {
            return false;
        }

        String dir = next > current ? "放宽" : "缩短";
        String explain = "因为" + String.join("；", reasons) + "，把浇水间隔从 "
                + current + " 天" + dir + "到 " + next + " 天。";

        plan.setEffectiveIntervalDays(next);
        plan.setLlmExplain(explain);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);

        reschedule(plan, today, next);

        log.info("[自适应-{}] 植物 {} 浇水间隔 {} → {} 天：{}", trigger, userPlantId, current, next, explain);
        return true;
    }

    /**
     * 重排未来任务：先清掉今天及以后还没做的，再从最近一次实际浇水往后铺 HORIZON 条。
     *
     * <p>必须「先删再插」而不是原地改日期：uk_plan_due(plan_id, due_date) 是唯一索引，
     * 原地改会让两条任务撞到同一天。
     */
    private void reschedule(CarePlanEntity plan, LocalDate today, int interval) {
        List<CareTaskEntity> pending = taskMapper.selectList(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getPlanId, plan.getId())
                .eq(CareTaskEntity::getStatus, "PENDING")
                .ge(CareTaskEntity::getDueDate, today));
        if (!pending.isEmpty()) {
            taskMapper.deleteBatchIds(pending.stream().map(CareTaskEntity::getId).toList());
        }

        // 起点：最后一次已完成的浇水；没有就用今天
        CareTaskEntity lastDone = taskMapper.selectOne(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getPlanId, plan.getId())
                .eq(CareTaskEntity::getStatus, "DONE")
                .orderByDesc(CareTaskEntity::getDueDate)
                .last("limit 1"));
        LocalDate base = lastDone == null || lastDone.getDueDate() == null ? today : lastDone.getDueDate();
        LocalDate due = base.plusDays(interval);
        while (due.isBefore(today)) {
            due = due.plusDays(interval);
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < HORIZON_TASKS; i++) {
            boolean exists = taskMapper.selectCount(new LambdaQueryWrapper<CareTaskEntity>()
                    .eq(CareTaskEntity::getPlanId, plan.getId())
                    .eq(CareTaskEntity::getDueDate, due)) > 0;
            if (!exists) {
                CareTaskEntity t = new CareTaskEntity();
                t.setPlanId(plan.getId());
                t.setUserId(plan.getUserId());
                t.setUserPlantId(plan.getUserPlantId());
                t.setTaskType(plan.getPlanType());
                t.setDueDate(due);
                t.setStatus("PENDING");
                t.setReminded(0);
                t.setCreatedAt(now);
                t.setUpdatedAt(now);
                taskMapper.insert(t);
            }
            due = due.plusDays(interval);
        }
    }

    /* ==================== 系数计算 ==================== */

    private static class Coefficients {
        final Map<String, Double> factors = new LinkedHashMap<>();
        final Map<String, String> labels = new LinkedHashMap<>();
        final int base;

        Coefficients(Map<String, Double> f, Map<String, String> l, int base) {
            this.factors.putAll(f);
            this.labels.putAll(l);
            this.base = base;
        }

        double product() {
            double v = 1.0;
            for (double x : factors.values()) v *= x;
            return v;
        }
    }

    /**
     * 浇水系数：季节 × 光照 × 温度 × 湿度 × 盆材质 × 空调房。
     * 温度/湿度取用户环境（未填则跳过该系数，而不是当成 1.0 记进明细，
     * 否则用户会误以为系统真的读了天气）。
     */
    private Coefficients waterCoefficients(UserPlantEntity plant, PlantSpeciesEntity sp, LocalDate today) {
        Map<String, Double> f = new LinkedHashMap<>();
        Map<String, String> l = new LinkedHashMap<>();

        double season = seasonWaterFactor(today);
        f.put("season", round2(season));
        l.put("season", seasonLabel(today));

        String light = plant.getLightLevel() != null ? plant.getLightLevel() : sp.getLightRequirement();
        Double lc = lightFactor(light);
        if (lc != null) {
            f.put("light", round2(lc));
            l.put("light", lightLabel(light));
        }

        String pot = plant.getPotMaterial();
        Double pc = potFactor(pot);
        if (pc != null) {
            f.put("pot", round2(pc));
            l.put("pot", potLabel(pot));
        }

        int base = sp.getBaseWaterIntervalDays() == null ? 7 : sp.getBaseWaterIntervalDays();
        return new Coefficients(f, l, base);
    }

    /** 季节系数：生长季需水多（间隔短），休眠期少（间隔长） */
    private static double seasonWaterFactor(LocalDate d) {
        int m = d.getMonthValue();
        if (m >= 3 && m <= 5) return 0.8;    // 春
        if (m >= 6 && m <= 8) return 0.8;    // 夏
        if (m >= 9 && m <= 11) return 1.0;   // 秋
        return 1.5;                          // 冬
    }

    /** 施肥只看季节：生长期缩短一半，休眠期拉长或停肥 */
    private static double seasonFertilizerFactor(LocalDate d) {
        int m = d.getMonthValue();
        if (m >= 3 && m <= 8) return 0.7;
        if (m >= 9 && m <= 11) return 1.2;
        return 2.0;
    }

    private static String seasonCode(LocalDate d) {
        int m = d.getMonthValue();
        if (m >= 3 && m <= 5) return "SPRING";
        if (m >= 6 && m <= 8) return "SUMMER";
        if (m >= 9 && m <= 11) return "AUTUMN";
        return "WINTER";
    }

    private static String seasonLabel(LocalDate d) {
        switch (seasonCode(d)) {
            case "SPRING": return "春季（生长季）";
            case "SUMMER": return "夏季（生长季）";
            case "AUTUMN": return "秋季";
            default: return "冬季（休眠期）";
        }
    }

    private static Double lightFactor(String light) {
        if (light == null || light.isBlank()) return null;
        switch (light.trim().toUpperCase()) {
            case "DIRECT": return 0.85;
            case "BRIGHT_INDIRECT": return 1.0;
            case "MEDIUM": return 1.15;
            case "LOW": return 1.4;
            default: return null;
        }
    }

    public static String lightLabel(String light) {
        if (light == null) return "";
        switch (light.trim().toUpperCase()) {
            case "DIRECT": return "直射光";
            case "BRIGHT_INDIRECT": return "明亮散射光";
            case "MEDIUM": return "中等光照";
            case "LOW": return "弱光";
            default: return light;
        }
    }

    private static Double potFactor(String pot) {
        if (pot == null || pot.isBlank()) return null;
        switch (pot.trim().toUpperCase()) {
            case "TERRACOTTA": return 0.9;    // 陶盆透气，水分散失快
            case "PLASTIC": return 1.0;
            case "CERAMIC": return 1.1;       // 釉面陶瓷盆不透气，保水
            case "GLASS": return 1.15;
            default: return null;
        }
    }

    public static String potLabel(String pot) {
        if (pot == null) return "";
        switch (pot.trim().toUpperCase()) {
            case "TERRACOTTA": return "陶盆（透气）";
            case "PLASTIC": return "塑料盆";
            case "CERAMIC": return "釉面陶瓷盆";
            case "GLASS": return "玻璃盆（不透气）";
            default: return pot;
        }
    }

    private static int clamp(long v, int lo, int hi) {
        return (int) Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static String toJson(Map<String, Double> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        return sb.append('}').toString();
    }

    /* ==================== 内部 ==================== */

    private CarePlanEntity owned(Long userId, Long id) {
        CarePlanEntity p = planMapper.selectById(id);
        if (p == null || !Objects.equals(p.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "养护计划不存在");
        }
        return p;
    }

    public CarePlanVO toVO(CarePlanEntity p) {
        UserPlantEntity plant = plantMapper.selectById(p.getUserPlantId());
        PlantSpeciesEntity sp = plant == null || plant.getSpeciesId() == null
                ? null : speciesMapper.selectById(plant.getSpeciesId());

        LocalDate next = null;
        CareTaskEntity t = taskMapper.selectOne(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getPlanId, p.getId())
                .eq(CareTaskEntity::getStatus, "PENDING")
                .orderByAsc(CareTaskEntity::getDueDate)
                .last("limit 1"));
        if (t != null) next = t.getDueDate();

        return new CarePlanVO(
                p.getId(),
                p.getUserPlantId(),
                plant == null ? null : plant.getNickname(),
                sp == null ? null : sp.getCommonName(),
                sp == null ? null : sp.getEmoji(),
                plant == null ? null : plant.getLocation(),
                p.getPlanType(),
                p.getSeason(),
                p.getBaseIntervalDays(),
                p.getEffectiveIntervalDays(),
                p.getCoefficientDetail(),
                p.getLlmExplain(),
                next == null ? null : next.format(DATE),
                p.getIsActive() != null && p.getIsActive() == 1,
                p.getGeneratedBy());
    }

    /** 供任务服务复用：确认某计划属于该用户 */
    public CarePlanEntity planOwned(Long userId, Long planId) {
        return owned(userId, planId);
    }

    /**
     * 修改计划：只允许改用户真正能决定的字段。
     *
     * 刻意**不允许**直接改 effectiveIntervalDays —— 否则前端随手传个数
     * 就把系数计算出来的结果覆盖了，「说得清」这个价值主张就不再成立。
     * 要改间隔就重新生成（系数会重算），或者用任务的 snooze 推迟单次。
     */
    @Transactional
    public CarePlanVO update(Long userId, Long id, Map<String, Object> body) {
        CarePlanEntity p = owned(userId, id);
        if (body == null) return toVO(p);

        Object explain = body.get("llmExplain");
        if (explain != null) {
            p.setLlmExplain(String.valueOf(explain));
        }
        Object active = body.get("isActive");
        if (active instanceof Boolean) {
            p.setIsActive(((Boolean) active) ? 1 : 0);
            if (!((Boolean) active)) {
                p.setEndDate(LocalDate.now().minusDays(1));
                // 停用时清掉未完成任务，否则日历还在提醒
                taskMapper.delete(new LambdaQueryWrapper<CareTaskEntity>()
                        .eq(CareTaskEntity::getPlanId, p.getId())
                        .eq(CareTaskEntity::getStatus, "PENDING"));
            }
        }
        p.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(p);
        return toVO(p);
    }

    /** 停用计划（前端 DELETE 语义），不硬删，保留历史便于统计 */
    @Transactional
    public void disable(Long userId, Long id) {
        CarePlanEntity p = owned(userId, id);
        p.setIsActive(0);
        p.setEndDate(LocalDate.now().minusDays(1));
        p.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(p);
        taskMapper.delete(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getPlanId, p.getId())
                .eq(CareTaskEntity::getStatus, "PENDING"));
    }
}
