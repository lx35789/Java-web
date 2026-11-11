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
import com.greenthumb.vo.CareCalendarVO;
import com.greenthumb.vo.CareLogPage;
import com.greenthumb.vo.CareLogVO;
import com.greenthumb.vo.CareTaskVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 养护任务（日历）与养护记录。
 *
 * <p>关键业务规则：
 * <ul>
 *   <li>完成任务时**同时写一条 care_log**，并让 task.care_log_id 指回去。
 *       这样"做过什么"和"该做什么"能对上，后续算浇水频率也靠日志。</li>
 *   <li>跳过（SKIPPED）不写日志 —— 跳过意味着没做，写日志会污染数据。</li>
 *   <li>PENDING 但已过期的任务，查询时**动态**标记为 OVERDUE，
 *       不改库：否则需要定时任务去刷状态，多一个失败点。</li>
 * </ul>
 */
@Service
public class CareTaskService {

    private static final Logger log = LoggerFactory.getLogger(CareTaskService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CareTaskMapper taskMapper;
    private final CarePlanMapper planMapper;
    private final CareLogMapper logMapper;
    private final UserPlantMapper plantMapper;
    private final PlantSpeciesMapper speciesMapper;

    public CareTaskService(CareTaskMapper taskMapper, CarePlanMapper planMapper,
                           CareLogMapper logMapper, UserPlantMapper plantMapper,
                           PlantSpeciesMapper speciesMapper) {
        this.taskMapper = taskMapper;
        this.planMapper = planMapper;
        this.logMapper = logMapper;
        this.plantMapper = plantMapper;
        this.speciesMapper = speciesMapper;
    }

    /* ==================== 查询 ==================== */

    public List<CareTaskVO> list(Long userId, String status, Long userPlantId,
                                 String from, String to) {
        LambdaQueryWrapper<CareTaskEntity> w = new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getUserId, userId)
                .orderByAsc(CareTaskEntity::getDueDate)
                .orderByAsc(CareTaskEntity::getId);
        if (status != null && !status.isBlank()) {
            w.eq(CareTaskEntity::getStatus, status.trim().toUpperCase());
        }
        if (userPlantId != null) {
            w.eq(CareTaskEntity::getUserPlantId, userPlantId);
        }
        if (from != null && !from.isBlank()) {
            w.ge(CareTaskEntity::getDueDate, LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            w.le(CareTaskEntity::getDueDate, LocalDate.parse(to));
        }
        return toVOs(taskMapper.selectList(w));
    }

    public List<CareTaskVO> today(Long userId) {
        LocalDate d = LocalDate.now();
        // 今天到期 + 已逾期未处理的，一起给（逾期的不主动推给用户就永远沉底了）
        List<CareTaskEntity> rows = taskMapper.selectList(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getUserId, userId)
                .in(CareTaskEntity::getStatus, "PENDING", "OVERDUE")
                .le(CareTaskEntity::getDueDate, d)
                .orderByAsc(CareTaskEntity::getDueDate));
        return toVOs(rows);
    }

    /** 日历：按月返回，结构对齐前端 mock（{month, days:[{date,tasks}], summary}） */
    public CareCalendarVO calendar(Long userId, String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "month 格式应为 yyyy-MM");
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<CareTaskVO> rows = toVOs(taskMapper.selectList(new LambdaQueryWrapper<CareTaskEntity>()
                .eq(CareTaskEntity::getUserId, userId)
                .between(CareTaskEntity::getDueDate, from, to)
                .orderByAsc(CareTaskEntity::getDueDate)));

        // 只返回有任务的日期，按日期升序；前端自己在日历格子里按 date 查表
        Map<String, List<CareTaskVO>> grouped = new LinkedHashMap<>();
        for (CareTaskVO t : rows) {
            grouped.computeIfAbsent(t.dueDate(), k -> new ArrayList<>()).add(t);
        }
        List<CareCalendarVO.Day> days = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<CareTaskVO>> e : grouped.entrySet()) {
            days.add(new CareCalendarVO.Day(e.getKey(), e.getValue()));
        }

        int pending = 0, done = 0, overdue = 0;
        for (CareTaskVO t : rows) {
            switch (t.status() == null ? "" : t.status()) {
                case "DONE" -> done++;
                case "OVERDUE" -> overdue++;
                case "PENDING" -> pending++;
                default -> { }
            }
        }
        return new CareCalendarVO(ym.toString(), days,
                new CareCalendarVO.Summary(rows.size(), pending, done, overdue));
    }

    /* ==================== 操作 ==================== */

    /**
     * 完成任务：写一条养护记录并回填关联。
     *
     * @param body 可带 amount/unit/detail/photoId，用于记录"浇了多少毫升"
     */
    @Transactional
    public CareTaskVO complete(Long userId, Long taskId, Map<String, Object> body) {
        CareTaskEntity task = owned(userId, taskId);
        if ("DONE".equals(task.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "这个任务已经完成过了");
        }

        LocalDateTime now = LocalDateTime.now();

        CareLogEntity logRow = new CareLogEntity();
        logRow.setUserId(userId);
        logRow.setUserPlantId(task.getUserPlantId());
        logRow.setTaskId(task.getId());
        logRow.setLogType(task.getTaskType());
        logRow.setLoggedAt(now);
        logRow.setCreatedAt(now);
        logRow.setDeleted(0);
        if (body != null) {
            Object amt = body.get("amount");
            if (amt instanceof Number) {
                logRow.setAmount(BigDecimal.valueOf(((Number) amt).doubleValue()));
            }
            logRow.setUnit(str(body.get("unit")));
            logRow.setDetail(str(body.get("detail")));
            Object pid = body.get("photoId");
            if (pid instanceof Number) {
                logRow.setPhotoId(((Number) pid).longValue());
            }
        }
        logMapper.insert(logRow);

        task.setStatus("DONE");
        task.setCompletedAt(now);
        task.setCareLogId(logRow.getId());
        task.setUpdatedAt(now);
        taskMapper.updateById(task);

        // 完成后补一条新任务，让日历不断档。
        //
        // 注意：表上有唯一索引 uk_plan_due(plan_id, due_date)，
        // 同一个计划同一天不能有两条任务。而生成计划时已经一次展开 HORIZON_TASKS 条，
        // 所以这里不能“完成一条就无条件 +interval 插一条”——那会撞上已存在的
        // 第二条任务（实测：Duplicate entry '1-2026-12-23' for key 'uk_plan_due'）。
        //
        // 正确做法：只有当被完成的任务是「该计划里最晚的一条」时才往后延，
        // 这样任务链始终保持约 HORIZON_TASKS 条的储备，且永不重复。
        CarePlanEntity plan = planMapper.selectById(task.getPlanId());
        if (plan != null && plan.getIsActive() != null && plan.getIsActive() == 1
                && plan.getEffectiveIntervalDays() != null) {
            CareTaskEntity latest = taskMapper.selectOne(new LambdaQueryWrapper<CareTaskEntity>()
                    .eq(CareTaskEntity::getPlanId, plan.getId())
                    .orderByDesc(CareTaskEntity::getDueDate)
                    .last("limit 1"));
            // 只有当刚完成的就是最靠后的那条，才往后延一条
            boolean completedIsLatest = latest == null || latest.getId().equals(task.getId());
            if (completedIsLatest) {
                CareTaskEntity next = new CareTaskEntity();
                next.setPlanId(plan.getId());
                next.setUserId(userId);
                next.setUserPlantId(task.getUserPlantId());
                next.setTaskType(task.getTaskType());
                next.setDueDate(task.getDueDate().plusDays(plan.getEffectiveIntervalDays()));
                next.setStatus("PENDING");
                next.setReminded(0);
                next.setCreatedAt(now);
                next.setUpdatedAt(now);
                taskMapper.insert(next);
            }
        }

        log.info("完成任务 {} → 养护记录 {}", taskId, logRow.getId());
        return toVO(task);
    }

    @Transactional
    public CareTaskVO skip(Long userId, Long taskId) {
        CareTaskEntity task = owned(userId, taskId);
        task.setStatus("SKIPPED");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toVO(task);
    }

    @Transactional
    public CareTaskVO snooze(Long userId, Long taskId, String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请提供新的到期日期");
        }
        LocalDate d;
        try {
            d = LocalDate.parse(dueDate.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
        CareTaskEntity task = owned(userId, taskId);
        task.setDueDate(d);
        task.setStatus("PENDING");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toVO(task);
    }

    /* ==================== 内部 ==================== */

    private CareTaskEntity owned(Long userId, Long id) {
        CareTaskEntity t = taskMapper.selectById(id);
        if (t == null || !Objects.equals(t.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return t;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public List<CareTaskVO> toVOs(List<CareTaskEntity> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, UserPlantEntity> plants = new HashMap<>();
        Map<Long, PlantSpeciesEntity> species = new HashMap<>();
        for (CareTaskEntity t : rows) {
            if (!plants.containsKey(t.getUserPlantId())) {
                UserPlantEntity p = plantMapper.selectById(t.getUserPlantId());
                plants.put(t.getUserPlantId(), p);
                if (p != null && p.getSpeciesId() != null && !species.containsKey(p.getSpeciesId())) {
                    species.put(p.getSpeciesId(), speciesMapper.selectById(p.getSpeciesId()));
                }
            }
        }
        List<CareTaskVO> out = new ArrayList<>(rows.size());
        for (CareTaskEntity t : rows) {
            out.add(build(t, plants.get(t.getUserPlantId()), species));
        }
        return out;
    }

    private CareTaskVO toVO(CareTaskEntity t) {
        UserPlantEntity p = plantMapper.selectById(t.getUserPlantId());
        PlantSpeciesEntity sp = p == null || p.getSpeciesId() == null
                ? null : speciesMapper.selectById(p.getSpeciesId());
        Map<Long, PlantSpeciesEntity> spMap = new HashMap<>();
        if (sp != null) spMap.put(sp.getId(), sp);
        return build(t, p, spMap);
    }

    private CareTaskVO build(CareTaskEntity t, UserPlantEntity p, Map<Long, PlantSpeciesEntity> spMap) {
        PlantSpeciesEntity sp = p == null || p.getSpeciesId() == null ? null : spMap.get(p.getSpeciesId());

        // 过期判定放在这里：库里仍是 PENDING，但日期已过 → 展示为 OVERDUE
        String status = t.getStatus();
        boolean overdue = "PENDING".equals(status)
                && t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now());

        return new CareTaskVO(
                t.getId(),
                t.getPlanId(),
                t.getUserPlantId(),
                t.getTaskType(),
                t.getDueDate() == null ? null : t.getDueDate().format(DATE),
                overdue ? "OVERDUE" : status,
                t.getCompletedAt() == null ? null : t.getCompletedAt().format(DT),
                p == null ? null : p.getNickname(),
                sp == null ? null : sp.getCommonName(),
                sp == null ? null : sp.getEmoji(),
                t.getCareLogId(),
                t.getNote());
    }

    /* ---------- 养护记录 ---------- */

    public CareLogPage logs(Long userId, Long userPlantId, String logType, Integer limit) {
        LambdaQueryWrapper<CareLogEntity> w = new LambdaQueryWrapper<CareLogEntity>()
                .eq(CareLogEntity::getUserId, userId)
                .orderByDesc(CareLogEntity::getLoggedAt);
        if (userPlantId != null) w.eq(CareLogEntity::getUserPlantId, userPlantId);
        if (logType != null && !logType.isBlank()) {
            w.eq(CareLogEntity::getLogType, logType.trim().toUpperCase());
        }
        w.last("limit " + Math.max(1, Math.min(limit == null ? 100 : limit, 500)));
        List<CareLogEntity> rows = logMapper.selectList(w);

        Map<Long, UserPlantEntity> plants = new HashMap<>();
        Map<Long, PlantSpeciesEntity> sps = new HashMap<>();
        List<CareLogVO> out = new ArrayList<>(rows.size());
        for (CareLogEntity l : rows) {
            UserPlantEntity p = plants.computeIfAbsent(l.getUserPlantId(), plantMapper::selectById);
            PlantSpeciesEntity sp = p == null || p.getSpeciesId() == null
                    ? null : sps.computeIfAbsent(p.getSpeciesId(), speciesMapper::selectById);
            out.add(new CareLogVO(
                    l.getId(), l.getUserPlantId(), l.getTaskId(), l.getLogType(),
                    l.getAmount() == null ? null : l.getAmount().doubleValue(),
                    l.getUnit(), l.getDetail(), l.getPhotoId(),
                    l.getLoggedAt() == null ? null : l.getLoggedAt().format(DT),
                    p == null ? null : p.getNickname(),
                    sp == null ? null : sp.getCommonName(),
                    sp == null ? null : sp.getEmoji()));
        }
        // 前端 logs.vue 读的是 {list, total}，不是裸数组
        return new CareLogPage(out, out.size());
    }

    @Transactional
    public CareLogVO createLog(Long userId, Map<String, Object> body) {
        if (body == null) throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        Object plantId = body.get("userPlantId");
        if (!(plantId instanceof Number)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定植物");
        }
        Long pid = ((Number) plantId).longValue();
        UserPlantEntity p = plantMapper.selectById(pid);
        if (p == null || !Objects.equals(p.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
        }
        String type = str(body.get("logType"));
        if (type == null || type.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择记录类型");
        }

        LocalDateTime now = LocalDateTime.now();
        CareLogEntity l = new CareLogEntity();
        l.setUserId(userId);
        l.setUserPlantId(pid);
        l.setLogType(type.trim().toUpperCase());
        Object amt = body.get("amount");
        if (amt instanceof Number) l.setAmount(BigDecimal.valueOf(((Number) amt).doubleValue()));
        l.setUnit(str(body.get("unit")));
        l.setDetail(str(body.get("detail")));
        Object tid = body.get("taskId");
        if (tid instanceof Number) l.setTaskId(((Number) tid).longValue());
        Object pid2 = body.get("photoId");
        if (pid2 instanceof Number) l.setPhotoId(((Number) pid2).longValue());
        String when = str(body.get("loggedAt"));
        l.setLoggedAt(when == null || when.isBlank() ? now
                : LocalDateTime.parse(when.replace('T', ' ').trim(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")));
        l.setCreatedAt(now);
        l.setDeleted(0);
        logMapper.insert(l);

        PlantSpeciesEntity sp = p.getSpeciesId() == null ? null : speciesMapper.selectById(p.getSpeciesId());
        return new CareLogVO(l.getId(), pid, l.getTaskId(), l.getLogType(),
                l.getAmount() == null ? null : l.getAmount().doubleValue(),
                l.getUnit(), l.getDetail(), l.getPhotoId(),
                l.getLoggedAt().format(DT), p.getNickname(),
                sp == null ? null : sp.getCommonName(), sp == null ? null : sp.getEmoji());
    }

    @Transactional
    public void deleteLog(Long userId, Long id) {
        CareLogEntity l = logMapper.selectById(id);
        if (l == null || !Objects.equals(l.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "养护记录不存在");
        }
        logMapper.deleteById(id);
    }

    /**
     * 修改养护记录：只改记录内容（量/单位/备注），不改类型与时间。
     * 类型改了会让历史统计口径不一致（比如把一次浇水改成施肥）；
     * 时间改了会让浇水频率推算失真。要改这两项就删了重建。
     */
    @Transactional
    public CareLogVO updateLog(Long userId, Long id, Map<String, Object> body) {
        CareLogEntity l = logMapper.selectById(id);
        if (l == null || !Objects.equals(l.getUserId(), userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "养护记录不存在");
        }
        if (body != null) {
            if (body.containsKey("amount")) {
                Object amt = body.get("amount");
                l.setAmount(amt instanceof Number
                        ? BigDecimal.valueOf(((Number) amt).doubleValue()) : null);
            }
            if (body.containsKey("unit")) l.setUnit(str(body.get("unit")));
            if (body.containsKey("detail")) l.setDetail(str(body.get("detail")));
        }
        logMapper.updateById(l);

        UserPlantEntity p = plantMapper.selectById(l.getUserPlantId());
        PlantSpeciesEntity sp = p == null || p.getSpeciesId() == null
                ? null : speciesMapper.selectById(p.getSpeciesId());
        return new CareLogVO(l.getId(), l.getUserPlantId(), l.getTaskId(), l.getLogType(),
                l.getAmount() == null ? null : l.getAmount().doubleValue(),
                l.getUnit(), l.getDetail(), l.getPhotoId(),
                l.getLoggedAt() == null ? null : l.getLoggedAt().format(DT),
                p == null ? null : p.getNickname(),
                sp == null ? null : sp.getCommonName(), sp == null ? null : sp.getEmoji());
    }
}
