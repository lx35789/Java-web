package com.greenthumb.recognition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别调度器：按顺序尝试所有已配置的供应商，任何一家成功就返回。
 * <p>
 * ★ 三条铁律（见 docs/05）：
 *   ① 供应商挂掉绝不向上抛异常 —— 用户看到的是「请手动选择」，不是 500
 *   ② 哪家成功就把名字记下来返回给前端（前端会显示「由百度识别」角标）
 *   ③ 全部不可用时返回 degraded=true，由前端引导手动选择
 * <p>
 * 顺序由 Spring 注入的 Bean 顺序决定；这里显式按 name 排序，
 * 让 BAIDU 一定排在 PLANTNET 前面，避免依赖 Bean 扫描顺序。
 */
@Component
public class RecognitionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RecognitionOrchestrator.class);

    /**
     * 降级链顺序：本地自研模型 → 百度 → Pl@ntNet。
     * <p>
     * 本地模型排最前：不需要 key、不走外网、响应最快；它只覆盖 6 个物种，
     * 认不出（置信度不足）时会自动落到后两家。百度排在 Pl@ntNet 前：
     * 中文名返回更准，配额也更宽。
     */
    private static final List<String> ORDER = List.of("SELF_MODEL", "BAIDU", "PLANTNET");

    /**
     * 候选置信度地板：低于该值的候选不作为识别结果返回。
     * <p>
     * 必要性：外部 API 对「不像植物」的图片也会硬返回几个结果，
     * 实测 Pl@ntNet 对一张合成图给出 0.03–0.07 的候选。
     * 这种结果喂给用户只会误导——宁可返回空、引导手动选择。
     */
    private static final double MIN_CONFIDENCE = 0.10;

    private final List<RecognitionProvider> providers;

    public RecognitionOrchestrator(List<RecognitionProvider> providers) {
        List<RecognitionProvider> sorted = new ArrayList<>(providers);
        sorted.sort((a, b) -> {
            int ia = ORDER.indexOf(a.name());
            int ib = ORDER.indexOf(b.name());
            return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
        });
        this.providers = List.copyOf(sorted);
        log.info("识别降级链已装配：{}", this.providers.stream()
                .map(p -> p.name() + (p.isConfigured() ? "(已配置)" : "(未配置)"))
                .toList());
    }

    /** 调度结果 */
    public record Outcome(String provider, List<RecognitionCandidate> candidates,
                          boolean degraded, boolean fallbackUsed, int latencyMs) {
    }

    public Outcome recognize(byte[] imageBytes, String filename) {
        long start = System.currentTimeMillis();
        boolean primaryTried = false;
        boolean fallbackUsed = false;

        for (RecognitionProvider p : providers) {
            if (!p.isConfigured()) {
                continue;
            }
            if (primaryTried) {
                fallbackUsed = true;
            }
            primaryTried = true;
            try {
                List<RecognitionCandidate> list = p.recognize(imageBytes, filename);
                int cost = (int) (System.currentTimeMillis() - start);
                // 过滤掉明显不可信的候选；全被过滤掉就当作这家没认出，继续降级
                List<RecognitionCandidate> kept = list.stream()
                        .filter(c -> c.confidence() >= MIN_CONFIDENCE)
                        .toList();
                if (!kept.isEmpty()) {
                    log.info("识别成功，provider={} 候选={} 耗时={}ms", p.name(), kept.size(), cost);
                    return new Outcome(p.name(), kept, false, fallbackUsed, cost);
                }
                if (!list.isEmpty()) {
                    log.warn("provider={} 的候选置信度均低于 {}，判为未识别，继续降级",
                            p.name(), MIN_CONFIDENCE);
                } else {
                    log.warn("provider={} 未返回候选，继续降级", p.name());
                }
            } catch (Exception e) {
                log.warn("provider={} 调用失败，继续降级：{}", p.name(), e.getMessage());
            }
        }

        // 全部不可用 / 全部失败
        int cost = (int) (System.currentTimeMillis() - start);
        log.warn("识别降级链全部失败，degraded=true，耗时={}ms", cost);
        return new Outcome("NONE", List.of(), true, fallbackUsed, cost);
    }
}
