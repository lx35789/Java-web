package com.greenthumb.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 大模型客户端（OpenAI 兼容协议）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>厂商无关</b>：阿里云百炼、智谱、DeepSeek、Moonshot 都提供 OpenAI 兼容端点，
 *       换厂商只改 application.yml 里的 base-url / vision-model，代码一行不动。</li>
 *   <li><b>永不抛异常</b>：调用失败返回 {@code ok=false} 的结果对象，由上层决定降级策略，
 *       不让一次外部故障把整个诊断接口打成 500。</li>
 *   <li><b>强制 HTTP/1.1</b>：JDK HttpClient 默认会尝试 h2c 升级，某些网关不认会导致
 *       "Unsupported upgrade request"。这个坑之前踩过。</li>
 *   <li><b>有超时</b>：连接 5 秒 / 请求 30 秒。绝不允许无限等待。</li>
 * </ul>
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String visionModel;
    private final String textModel;
    private final int timeoutMs;
    private final boolean enabled;

    private final HttpClient http;

    public LlmClient(@Value("${greenthumb.llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
                     @Value("${greenthumb.llm.api-key:}") String apiKey,
                     @Value("${greenthumb.llm.vision-model:qwen-vl-max}") String visionModel,
                     @Value("${greenthumb.llm.text-model:qwen-plus}") String textModel,
                     @Value("${greenthumb.llm.timeout-ms:30000}") int timeoutMs,
                     @Value("${greenthumb.llm.enabled:true}") boolean enabled) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.visionModel = visionModel;
        this.textModel = textModel;
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(5000))
                .build();
        log.info("大模型客户端：{} / 视觉={} / 文字={} / 已配置={}",
                this.baseUrl, visionModel, textModel, isConfigured());
    }

    /** 是否已配置可用的 API Key。未配置时上层应直接走"暂无 AI 能力"的分支，不要发请求。 */
    public boolean isConfigured() {
        return enabled && !apiKey.isEmpty();
    }

    public String visionModel() {
        return visionModel;
    }

    /** 调用结果。ok=false 时 error 一定有值，上层据此降级。 */
    public record Result(boolean ok, String content, String error, long latencyMs) {
        static Result fail(String msg, long ms) {
            return new Result(false, null, msg, ms);
        }
    }

    /**
     * 视觉问答：图片 + 提示词 → 模型回答。
     *
     * @param image 图片二进制
     * @param mime  图片 MIME，如 image/jpeg
     */
    public Result chatVision(byte[] image, String mime, String system, String user) {
        if (!isConfigured()) {
            return Result.fail("未配置大模型 API Key（greenthumb.llm.api-key）", 0);
        }
        long t0 = System.currentTimeMillis();
        try {
            String dataUrl = "data:" + (mime == null || mime.isBlank() ? "image/jpeg" : mime)
                    + ";base64," + Base64.getEncoder().encodeToString(image);

            ObjectNode body = JSON.createObjectNode();
            body.put("model", visionModel);
            body.put("temperature", 0.1);          // 诊断任务要确定性，温度压到最低
            body.put("max_tokens", 1200);
            ObjectNode fmt = body.putObject("response_format");
            fmt.put("type", "json_object");        // 尽量让厂商返回纯 JSON

            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");
            content.addObject().put("type", "text").put("text", user);
            ObjectNode img = content.addObject();
            img.put("type", "image_url");
            img.putObject("image_url").put("url", dataUrl);

            return post(body, t0);
        } catch (Exception e) {
            return Result.fail("构造请求失败：" + e.getMessage(), System.currentTimeMillis() - t0);
        }
    }

    /** 纯文字问答（第 3 层）。 */
    public Result chatText(String system, String user) {
        if (!isConfigured()) {
            return Result.fail("未配置大模型 API Key（greenthumb.llm.api-key）", 0);
        }
        long t0 = System.currentTimeMillis();
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", textModel);
            body.put("temperature", 0.3);
            body.put("max_tokens", 1200);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            messages.addObject().put("role", "user").put("content", user);
            return post(body, t0);
        } catch (Exception e) {
            return Result.fail("构造请求失败：" + e.getMessage(), System.currentTimeMillis() - t0);
        }
    }

    private Result post(ObjectNode body, long t0) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long ms = System.currentTimeMillis() - t0;

            if (resp.statusCode() != 200) {
                log.warn("大模型返回 {}：{}", resp.statusCode(),
                        resp.body() == null ? "" : resp.body().substring(0, Math.min(200, resp.body().length())));
                return Result.fail("HTTP " + resp.statusCode(), ms);
            }

            JsonNode root = JSON.readTree(resp.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Result.fail("响应里没有 choices", ms);
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return Result.fail("模型返回空内容", ms);
            }
            return new Result(true, content, null, ms);
        } catch (java.net.http.HttpTimeoutException e) {
            return Result.fail("超时（" + timeoutMs + "ms）", System.currentTimeMillis() - t0);
        } catch (Exception e) {
            return Result.fail(e.getClass().getSimpleName() + "：" + e.getMessage(),
                    System.currentTimeMillis() - t0);
        }
    }
}
