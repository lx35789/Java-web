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

    /** 降级链顺序 */
    private static final List<String> ORDER = List.of("BAIDU", "PLANTNET", "SELF_MODEL");

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
                if (!list.isEmpty()) {
                    log.info("识别成功，provider={} 候选={} 耗时={}ms", p.name(), list.size(), cost);
                    return new Outcome(p.name(), list, false, fallbackUsed, cost);
                }
                log.warn("provider={} 未返回候选，继续降级", p.name());
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
