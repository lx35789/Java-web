package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.entity.QaMessageEntity;
import com.greenthumb.entity.QaSessionEntity;
import com.greenthumb.mapper.QaMessageMapper;
import com.greenthumb.mapper.QaSessionMapper;
import com.greenthumb.service.llm.LlmClient;
import com.greenthumb.vo.MyPlantDetailVO;
import com.greenthumb.vo.ObservationVO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 养护顾问：把「这盆植物自己的数据」拼进系统提示词，再交给千问多轮对话。
 *
 * <p>为什么不是单纯接一个聊天框：
 * 通用大模型给的是「绿萝黄叶的 10 种原因」，用户要的是「我这盆上礼拜浇过水、
 * 放在北阳台、健康指数刚掉了 61 分，现在该怎么办」。所以每次提问前都会重新读一次
 * 植物档案 + 养护计划 + 最近浇水/施肥记录 + 健康曲线 + 最近诊断，拼成一段事实清单。
 * 模型只负责「基于这些事实给建议」，不负责猜。
 *
 * <p>流式：走 SSE，边生成边推。qwen-plus 一次回答 3~8 秒，非流式等待体验太差。
 */
@Service
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final MediaType TEXT_UTF8 = MediaType.valueOf("text/plain;charset=UTF-8");

    /** 带进上下文的历史轮数（user+assistant 合计条数） */
    private static final int HISTORY_LIMIT = 12;

    private final QaSessionMapper sessionMapper;
    private final QaMessageMapper messageMapper;
    private final MyPlantService plantService;
    private final PlantObservationService observationService;
    private final LlmClient llm;

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "qa-stream");
        t.setDaemon(true);
        return t;
    });

    public QaService(QaSessionMapper sessionMapper,
                     QaMessageMapper messageMapper,
                     MyPlantService plantService,
                     PlantObservationService observationService,
                     LlmClient llm) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.plantService = plantService;
        this.observationService = observationService;
        this.llm = llm;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    /* ==================== 会话 ==================== */

    public List<Map<String, Object>> sessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<QaSessionEntity>()
                        .eq(QaSessionEntity::getUserId, userId)
                        .eq(QaSessionEntity::getDeleted, 0)
                        .orderByDesc(QaSessionEntity::getUpdatedAt)
                        .last("limit 30"))
                .stream().map(this::toSessionVO).toList();
    }

    public Map<String, Object> createSession(Long userId, Map<String, Object> body) {
        QaSessionEntity s = new QaSessionEntity();
        s.setUserId(userId);
        s.setUserPlantId(asLong(body.get("userPlantId")));
        s.setDiagnosisContext(trim(asString(body.get("diagnosisContext")), 2000));
        String content = trim(asString(body.get("content")), 40);
        s.setTitle(content == null || content.isBlank() ? "新对话" : content);
        s.setMessageCount(0);
        s.setDeleted(0);
        // 显式写时间：MySQL 的 DEFAULT CURRENT_TIMESTAMP 不会回填到实体，
        // 不设的话新建会话返回给前端的 createdAt 是 null（列表里排序/显示都难看）
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(s.getCreatedAt());
        sessionMapper.insert(s);
        return toSessionVO(s);
    }

    /** 会话详情：会话信息 + 全部消息。 */
    public Map<String, Object> detail(Long userId, Long sessionId) {
        QaSessionEntity s = owned(userId, sessionId);
        List<Map<String, Object>> messages = messageMapper.selectList(
                        new LambdaQueryWrapper<QaMessageEntity>()
                                .eq(QaMessageEntity::getSessionId, sessionId)
                                .orderByAsc(QaMessageEntity::getId))
                .stream().map(this::toMessageVO).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", toSessionVO(s));
        out.put("messages", messages);
        return out;
    }

    /** 改标题 / 换关联植物（只用于在会话里切换「问的是哪盆」）。 */
    public Map<String, Object> updateSession(Long userId, Long sessionId, Map<String, Object> body) {
        QaSessionEntity s = owned(userId, sessionId);
        if (body.containsKey("userPlantId")) s.setUserPlantId(asLong(body.get("userPlantId")));
        if (body.containsKey("title")) s.setTitle(trim(asString(body.get("title")), 80));
        if (body.containsKey("diagnosisContext")) {
            s.setDiagnosisContext(trim(asString(body.get("diagnosisContext")), 2000));
        }
        s.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(s);
        return toSessionVO(s);
    }

    public void deleteSession(Long userId, Long sessionId) {
        QaSessionEntity s = owned(userId, sessionId);
        s.setDeleted(1);
        sessionMapper.updateById(s);
    }

    /* ==================== 提问（SSE 流式） ==================== */

    /**
     * 提问并流式返回。事件序列：
     * <pre>
     *   meta   {sessionId,userMessageId}
     *   delta  {text}       × N
     *   done   {assistantMessage:{...}}
     *   error  {message}    出错时替代 done
     * </pre>
     */
    public SseEmitter ask(Long userId, Long sessionId, Map<String, Object> body) {
        QaSessionEntity session = owned(userId, sessionId);
        String content = asString(body.get("content"));
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "问题不能为空");
        }
        content = content.trim();

        // 前端切了植株就顺手记下来，下次打开还是这盆
        Long plantId = asLong(body.get("userPlantId"));
        if (plantId != null && !plantId.equals(session.getUserPlantId())) {
            session.setUserPlantId(plantId);
        }
        String diagnosisContext = trim(asString(body.get("diagnosisContext")), 2000);
        if (diagnosisContext != null && !diagnosisContext.isBlank()) {
            session.setDiagnosisContext(diagnosisContext);
        }

        SseEmitter emitter = new SseEmitter(180_000L);
        final String question = content;
        final QaSessionEntity s = session;

        pool.execute(() -> {
            try {
                QaMessageEntity userMsg = insertMessage(s.getId(), "USER", question, null, 0);

                if (s.getMessageCount() == null || s.getMessageCount() == 0) {
                    s.setTitle(question.length() > 24 ? question.substring(0, 24) + "…" : question);
                }
                s.setMessageCount((int) count(s.getId()));
                s.setUpdatedAt(LocalDateTime.now());
                sessionMapper.updateById(s);

                send(emitter, "meta", Map.<String, Object>of(
                        "sessionId", s.getId(),
                        "userMessageId", userMsg.getId(),
                        "title", s.getTitle() == null ? "" : s.getTitle()));

                List<LlmClient.Message> messages = buildMessages(userId, s, question);

                LlmClient.Result r = llm.chatStream(messages, 0.6, 900, piece -> {
                    try {
                        send(emitter, "delta", Map.of("text", piece));
                    } catch (Exception e) {
                        // 转成 UncheckedIOException，让 LlmClient 停止读取模型流
                        throw new UncheckedIOException(new java.io.IOException(e.getMessage(), e));
                    }
                });

                if (!r.ok()) {
                    send(emitter, "error", Map.of("message", friendlyError(r.error())));
                    emitter.complete();
                    return;
                }

                QaMessageEntity assistant = insertMessage(s.getId(), "ASSISTANT", r.content(),
                        llm.isConfigured() ? "qwen-plus" : "unknown", (int) Math.min(r.latencyMs(), Integer.MAX_VALUE));
                s.setMessageCount((int) count(s.getId()));
                s.setUpdatedAt(LocalDateTime.now());
                sessionMapper.updateById(s);

                send(emitter, "done", Map.of("assistantMessage", toMessageVO(assistant)));
                emitter.complete();
            } catch (Exception e) {
                log.warn("AI 顾问流式回答失败：{}", e.getMessage());
                try {
                    send(emitter, "error", Map.of("message", friendlyError(e.getMessage())));
                } catch (Exception ignored) {
                    // 连接已断，无需处理
                }
                emitter.complete();
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.info("SSE 连接中断：{}", e.getMessage()));
        return emitter;
    }

    public void feedback(Long userId, Long messageId, Integer value) {
        QaMessageEntity m = messageMapper.selectById(messageId);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        owned(userId, m.getSessionId());
        m.setFeedback(value);
        messageMapper.updateById(m);
    }

    /* ==================== 上下文拼装 ==================== */

    private List<LlmClient.Message> buildMessages(Long userId, QaSessionEntity s, String question) {
        List<LlmClient.Message> list = new ArrayList<>();
        list.add(new LlmClient.Message("system", systemPrompt(userId, s)));

        List<QaMessageEntity> history = messageMapper.selectList(
                new LambdaQueryWrapper<QaMessageEntity>()
                        .eq(QaMessageEntity::getSessionId, s.getId())
                        .orderByDesc(QaMessageEntity::getId)
                        .last("limit " + HISTORY_LIMIT));
        // 上面是倒序取最近 N 条，这里翻回正序；已经包含刚落库的这条提问
        for (int i = history.size() - 1; i >= 0; i--) {
            QaMessageEntity m = history.get(i);
            String role = "USER".equals(m.getRole()) ? "user" : "assistant";
            list.add(new LlmClient.Message(role, m.getContent()));
        }
        return list;
    }

    private String systemPrompt(Long userId, QaSessionEntity s) {
        StringBuilder sb = new StringBuilder();
        sb.append("你叫「植语」，是一位资深室内植物养护顾问。用户正在和你一对一聊天，")
                .append("你要帮他把眼前这盆植物养好。今天是 ").append(LocalDate.now())
                .append("（").append(season()).append("）。\n");

        sb.append("\n【这盆植物的档案】\n");
        boolean hasPlant = false;
        if (s.getUserPlantId() != null) {
            try {
                MyPlantDetailVO p = plantService.detail(userId, s.getUserPlantId());
                hasPlant = true;
                sb.append("- 昵称：").append(nv(p.nickname())).append("（").append(nv(p.speciesName())).append("）\n");
                sb.append("- 摆放位置：").append(nv(p.location()))
                        .append("；光照条件：").append(nv(CarePlanService.lightLabel(p.lightLevel())))
                        .append("；盆器：").append(nv(CarePlanService.potLabel(p.potMaterial())))
                        .append(' ').append(potSizeCn(p.potSize()))
                        .append("；土壤：").append(soilCn(p.soilType())).append("\n");
                sb.append("- 当前健康状态：").append(healthCn(p.healthStatus())).append("\n");
                sb.append("- 最近一次浇水：").append(nv(p.lastWateredAt()))
                        .append("；下次浇水计划：").append(nv(p.nextWaterDueDate())).append("\n");
                if (p.carePlans() != null && !p.carePlans().isEmpty()) {
                    sb.append("- 养护节律：");
                    for (Object o : p.carePlans()) {
                        if (o instanceof Map<?, ?> m) {
                            sb.append(planCn(String.valueOf(m.get("planType"))))
                                    .append("每 ").append(m.get("effectiveIntervalDays")).append(" 天；");
                        }
                    }
                    sb.append("\n");
                    Object first = p.carePlans().get(0);
                    if (first instanceof Map<?, ?> m && m.get("llmExplain") != null) {
                        sb.append("- 上一次计划校准说明：").append(clip(String.valueOf(m.get("llmExplain")), 160)).append("\n");
                    }
                }
                if (p.recentLogs() != null && !p.recentLogs().isEmpty()) {
                    sb.append("- 最近养护记录：\n");
                    for (Object o : p.recentLogs()) {
                        if (o instanceof Map<?, ?> m) {
                            sb.append("  · ").append(m.get("loggedAt")).append(' ')
                                    .append(logCn(String.valueOf(m.get("logType"))));
                            Object detailRaw = m.get("detail");
                            String detail = detailRaw == null ? "" : String.valueOf(detailRaw);
                            if (!detail.isBlank()) sb.append("（").append(clip(detail, 40)).append("）");
                            sb.append("\n");
                        }
                    }
                }
                ObservationVO latest = observationService.latest(userId, s.getUserPlantId());
                if (latest != null && latest.healthIndex() != null) {
                    sb.append("- 最近一次状态快评：健康指数 ").append(latest.healthIndex())
                            .append("/100（").append(latest.observedAt() == null ? "—" : latest.observedAt().format(DT))
                            .append("）");
                    List<com.greenthumb.entity.PlantObservationEntity> recent =
                            observationService.recent(s.getUserPlantId(), 3);
                    if (recent != null && recent.size() >= 2) {
                        Integer prev = recent.get(1).getHealthIndex();
                        Integer now = recent.get(0).getHealthIndex();
                        if (prev != null && now != null) {
                            int d = now - prev;
                            sb.append("，较上次").append(d >= 0 ? "上升 " : "下降 ").append(Math.abs(d)).append(" 分");
                        }
                    }
                    sb.append("\n");
                }
                if (p.note() != null && !p.note().isBlank()) {
                    sb.append("- 用户备注：").append(clip(p.note(), 120)).append("\n");
                }
            } catch (Exception e) {
                log.info("拼装植物档案失败（plantId={}）：{}", s.getUserPlantId(), e.getMessage());
                sb.append("- （档案读取失败，按通用养护原则回答）\n");
            }
        }
        if (!hasPlant) {
            sb.append("- 用户这次没有指定具体植物，按室内观叶植物通用原则回答，并提醒他补充品种信息。\n");
        }

        if (s.getDiagnosisContext() != null && !s.getDiagnosisContext().isBlank()) {
            sb.append("\n【本次对话的起点：一次刚做完的诊断】\n")
                    .append(clip(s.getDiagnosisContext(), 900)).append("\n");
        }

        sb.append("""
                
                【回答要求】
                1. 必须结合上面这盆植物的真实情况（昵称、浇水间隔、上次浇水时间、健康状况），不要给放之四海皆准的模板。
                2. 先一句话给结论，再给 2~4 条可执行动作；必要处补一句判断依据。
                3. 中文口语，短句，不用 Markdown 标题和表格；总长不超过 300 字。
                4. 信息不足时先问 1~2 个关键问题，不要编造事实；不确定就说不确定。
                5. 涉及药剂只给成分名（如阿维菌素、吡虫啉）和安全提示，不给处方剂量。
                6. 只回答园艺养护相关问题；超出范围就礼貌说明你只负责养护。
                """);
        return sb.toString();
    }

    /* ==================== 工具方法 ==================== */

    private QaMessageEntity insertMessage(Long sessionId, String role, String content, String model, int latencyMs) {
        QaMessageEntity m = new QaMessageEntity();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setModel(model);
        m.setLatencyMs(latencyMs);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        return m;
    }

    private long count(Long sessionId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<QaMessageEntity>()
                .eq(QaMessageEntity::getSessionId, sessionId));
    }

    private QaSessionEntity owned(Long userId, Long sessionId) {
        QaSessionEntity s = sessionMapper.selectOne(new LambdaQueryWrapper<QaSessionEntity>()
                .eq(QaSessionEntity::getId, sessionId)
                .eq(QaSessionEntity::getUserId, userId)
                .eq(QaSessionEntity::getDeleted, 0));
        if (s == null) throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        return s;
    }

    private void send(SseEmitter emitter, String event, Map<String, Object> data) throws Exception {
        emitter.send(SseEmitter.event().name(event).data(JSON.writeValueAsString(data), TEXT_UTF8));
    }

    private Map<String, Object> toSessionVO(QaSessionEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("messageCount", s.getMessageCount() == null ? 0 : s.getMessageCount());
        m.put("userPlantId", s.getUserPlantId());
        m.put("diagnosisContext", s.getDiagnosisContext());
        m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().format(DT));
        m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().format(DT));
        return m;
    }

    private Map<String, Object> toMessageVO(QaMessageEntity m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("role", m.getRole());
        out.put("content", m.getContent());
        out.put("model", m.getModel());
        out.put("latencyMs", m.getLatencyMs());
        out.put("feedback", m.getFeedback());
        out.put("createdAt", m.getCreatedAt() == null ? null : m.getCreatedAt().format(DT));
        return out;
    }

    private String friendlyError(String raw) {
        if (raw == null) return "回答失败，请稍后重试";
        if (raw.contains("未配置大模型 API Key")) return "AI 服务还没配置千问 API Key，请让管理员在 application-dev.yml 里填 greenthumb.llm.api-key";
        if (raw.contains("超时")) return "千问响应超时了，可以再问一次，或把问题描述短一些";
        if (raw.startsWith("HTTP 401") || raw.startsWith("HTTP 403")) return "千问 API Key 无效或已过期";
        if (raw.startsWith("HTTP 429")) return "千问调用太频繁了，等几秒再试";
        if (raw.startsWith("HTTP 5")) return "千问服务暂时不可用，稍后再试";
        return "回答失败：" + raw;
    }

    private String season() {
        int m = LocalDate.now().getMonthValue();
        if (m >= 3 && m <= 5) return "春季";
        if (m >= 6 && m <= 8) return "夏季";
        if (m >= 9 && m <= 11) return "秋季";
        return "冬季";
    }

    private String healthCn(String s) {
        if (s == null) return "未知";
        return switch (s) {
            case "HEALTHY" -> "健康";
            case "WATCH" -> "需关注";
            case "SICK" -> "有病害";
            case "CRITICAL" -> "情况较差";
            default -> s;
        };
    }

    private String planCn(String t) {
        if (t == null) return "养护";
        return switch (t) {
            case "WATER" -> "浇水";
            case "FERTILIZE" -> "施肥";
            case "PRUNE" -> "修剪";
            case "MIST" -> "喷雾";
            case "REPOT" -> "换盆";
            default -> t;
        };
    }

    private String logCn(String t) {
        if (t == null) return "养护";
        return switch (t) {
            case "WATER" -> "浇水";
            case "FERTILIZE" -> "施肥";
            case "PRUNE" -> "修剪";
            case "MIST" -> "喷雾";
            case "REPOT" -> "换盆";
            case "PEST_CONTROL" -> "除虫";
            default -> t;
        };
    }

    private String nv(String s) {
        return s == null || s.isBlank() ? "未填" : s;
    }

    /** 枚举值不能直接进提示词：模型会把 BRIGHT_INDIRECT 原样抄进回答里 */
    private String potSizeCn(String size) {
        if (size == null || size.isBlank()) return "";
        return switch (size.trim().toUpperCase()) {
            case "SMALL" -> "小盆";
            case "MEDIUM" -> "中盆";
            case "LARGE" -> "大盆";
            default -> size;
        };
    }

    private String soilCn(String soil) {
        if (soil == null || soil.isBlank()) return "未填";
        return switch (soil.trim().toUpperCase()) {
            case "PEAT" -> "泥炭土";
            case "MIXED" -> "通用营养土";
            case "GARDEN" -> "园土";
            case "COCO" -> "椰糠";
            case "PON" -> "颗粒土";
            default -> soil;
        };
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Long asLong(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
