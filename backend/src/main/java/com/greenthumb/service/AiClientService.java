package com.greenthumb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 调用 Python AI 服务（FastAPI + ONNX Runtime）。
 * <p>
 * 设计原则：**这个类永远不抛异常**。
 * Python 服务没启动、模型没训练、网络超时——全部收敛成 available=false + reason，
 * 由上层决定怎么给用户提示。识别功能挂掉不能把整个应用带崩，这是硬要求。
 * <p>
 * 接口约定见 ai-service/app/main.py：
 * GET  /health                 → { diseaseModel: { available, reason, numClasses, meta } }
 * POST /v1/disease/predict     → multipart(file) → { available, reason, topK: [{code, species, disease, nameCn, confidence}] }
 */
@Service
public class AiClientService {

    private static final Logger log = LoggerFactory.getLogger(AiClientService.class);
    /** 健康检查结果缓存时长：避免每次诊断都多打一次 HTTP */
    private static final long HEALTH_CACHE_MS = 30_000L;

    private final HttpClient http;
    private final ObjectMapper json;
    private final String baseUrl;
    private final Duration timeout;

    private volatile long lastHealthCheck = 0L;
    private volatile boolean modelAvailable = false;
    private volatile String modelReason = "尚未探测";
    private volatile int numClasses = 0;

    public AiClientService(@Value("${greenthumb.ai-service.base-url:http://localhost:8000}") String baseUrl,
                           @Value("${greenthumb.ai-service.timeout-ms:6000}") long timeoutMs,
                           ObjectMapper json) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(Math.max(500L, timeoutMs));
        this.json = json;
        this.http = HttpClient.newBuilder()
                // 必须锁 HTTP/1.1：JDK HttpClient 默认会尝试 h2c 明文升级，
                // 而 uvicorn 不支持该升级握手 —— 表现为 GET 侥幸成功、带 body 的 POST
                // 被服务端记成 "Invalid HTTP request received" 并重置连接。
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(Math.max(500L, timeoutMs), 3000L)))
                .build();
        log.info("AI 服务地址 {}，单次超时 {} ms", this.baseUrl, timeoutMs);
    }

    /* ==================== 对外 ==================== */

    public record Health(boolean available, String reason, int numClasses) {
    }

    public record Item(String code, String nameCn, String species, String disease, double confidence) {
    }

    public record Prediction(boolean available, String reason, List<Item> topK, int latencyMs,
                             /** 该物种在本模型里支持的类别数；null 表示未知（未按物种过滤） */
                             Integer speciesClassCount,
                             /** 该物种支持的类别清单，用于告知用户「只认这几种」 */
                             List<String> speciesClasses) {

        /** 兼容旧调用：不关心覆盖度时用 */
        public Prediction(boolean available, String reason, List<Item> topK, int latencyMs) {
            this(available, reason, topK, latencyMs, null, List.of());
        }
    }

    /** 模型是否就绪（带 30 秒缓存） */
    public Health health() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck < HEALTH_CACHE_MS) {
            return new Health(modelAvailable, modelReason, numClasses);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(timeout).GET().build();
            HttpResponse<String> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode dm = json.readTree(resp.body()).path("diseaseModel");
            modelAvailable = dm.path("available").asBoolean(false);
            modelReason = dm.path("reason").isMissingNode() || dm.path("reason").isNull()
                    ? null : dm.path("reason").asText();
            numClasses = dm.path("numClasses").asInt(0);
            if (!modelAvailable) {
                log.warn("AI 模型未就绪：{}", modelReason);
            }
        } catch (Exception e) {
            modelAvailable = false;
            modelReason = "AI 服务不可达（" + baseUrl + "）：" + rootMessage(e);
        }
        lastHealthCheck = now;
        return new Health(modelAvailable, modelReason, numClasses);
    }

    /**
     * 病害分类。任何失败都返回 available=false，绝不抛异常。
     *
     * @param imageBytes 图片字节
     * @param filename   原文件名（仅用于 multipart 的 filename 字段）
     * @param topK       要几名的候选
     */
    public Prediction predictDisease(byte[] imageBytes, String filename, int topK) {
        return predictDisease(imageBytes, filename, topK, null);
    }

    /**
     * 带物种的病害识别。
     *
     * @param species 模型的物种 key（Aloe / Money_Plant / Rose ...）。
     *                AI 服务若有该物种的专用模型会优先使用（芦荟专用模型 68%，
     *                通用模型在同一批图上只有 8%）；没有专用模型自动退回通用模型。
     */
    public Prediction predictDisease(byte[] imageBytes, String filename, int topK, String species) {
        long t0 = System.currentTimeMillis();
        if (imageBytes == null || imageBytes.length == 0) {
            return new Prediction(false, "图片内容为空", List.of(), 0);
        }
        String boundary = "----GreenThumb" + UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] body = multipartBody(boundary, "file",
                    filename == null || filename.isBlank() ? "photo.jpg" : filename, imageBytes);

            int k = Math.max(1, Math.min(topK, 10));
            String url = baseUrl + "/v1/disease/predict?top_k=" + k;
            if (species != null && !species.isBlank()) {
                url += "&species=" + java.net.URLEncoder.encode(species, StandardCharsets.UTF_8);
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int latency = (int) (System.currentTimeMillis() - t0);

            if (resp.statusCode() != 200) {
                return new Prediction(false, "AI 服务返回 HTTP " + resp.statusCode(), List.of(), latency);
            }
            JsonNode root = json.readTree(resp.body());
            if (!root.path("available").asBoolean(false)) {
                String why = root.path("reason").isMissingNode() || root.path("reason").isNull()
                        ? "模型暂不可用" : root.path("reason").asText();
                return new Prediction(false, why, List.of(), latency);
            }

            Integer clsCount = root.path("speciesClassCount").isNumber()
                    ? root.path("speciesClassCount").asInt() : null;
            List<String> clsList = new ArrayList<>();
            for (JsonNode c : root.path("speciesClasses")) {
                clsList.add(c.asText());
            }

            List<Item> items = new ArrayList<>();
            for (JsonNode n : root.path("topK")) {
                items.add(new Item(
                        text(n, "code"), text(n, "nameCn"),
                        text(n, "species"), text(n, "disease"),
                        n.path("confidence").asDouble(0d)));
            }
            if (items.isEmpty()) {
                // 保留 AI 服务给出的具体原因（例如「图像与绿萝的病害特征不符，该物种概率合计仅 1.1%」），
                // 不要统一替换成"没有返回候选"——那样用户完全不知道发生了什么。
                String why = root.path("reason").isMissingNode() || root.path("reason").isNull()
                        ? "模型没有返回任何候选" : root.path("reason").asText();
                return new Prediction(false, why, List.of(), latency, clsCount, clsList);
            }
            return new Prediction(true, null, items, latency, clsCount, clsList);

        } catch (Exception e) {
            int latency = (int) (System.currentTimeMillis() - t0);
            log.warn("调用病害识别失败：{}", rootMessage(e));
            return new Prediction(false, "AI 服务调用失败：" + rootMessage(e), List.of(), latency);
        }
    }

    /* ==================== 内部 ==================== */

    /** 手拼 multipart/form-data —— JDK 的 HttpClient 没有内置的 multipart builder */
    private static byte[] multipartBody(String boundary, String field, String filename, byte[] content)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 512);
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + guessContentType(filename) + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String guessContentType(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".png")) {
            return "image/png";
        }
        if (f.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
