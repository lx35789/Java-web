package com.greenthumb.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 百度 AI 开放平台「植物识别」。
 * <p>
 * 文档：https://ai.baidu.com/ai-doc/IMAGERECOGNITION/3k3bc3vpx
 * <p>
 * 两个坑（见 docs/05 §8）：
 *   ① access_token 有效期 30 天，必须缓存，不能每次请求都换
 *   ② 图片要 base64 之后再 urlencode，且限制：base64 后 ≤4MB、最短边 ≥50px、最长边 ≤4096px
 * <p>
 * 配置（放 application-dev.yml，该文件不会进仓库）：
 * <pre>
 * greenthumb:
 *   recognition:
 *     baidu:
 *       api-key: 你的API_KEY
 *       secret-key: 你的SECRET_KEY
 * </pre>
 */
@Component
public class BaiduRecognitionProvider implements RecognitionProvider {

    private static final Logger log = LoggerFactory.getLogger(BaiduRecognitionProvider.class);
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String PLANT_URL = "https://aip.baidubce.com/rest/2.0/image-classify/v1/plant";

    private final RestClient http = buildClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 带超时的 HTTP 客户端。
     * <p>
     * ⚠️ {@code RestClient.builder().build()} 默认**没有任何超时**：外部接口一旦卡住，
     * 这个请求会一直挂着——前端只能转圈到 axios 超时，后端线程还被白白占住。
     * 所以必须显式设连接与读取超时（这正是"病害识别一直加载"的排查方向之一）。
     */
    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(12000);
        return RestClient.builder().requestFactory(f).build();
    }

    private final String apiKey;
    private final String secretKey;

    /** token 缓存 */
    private volatile String cachedToken;
    private volatile long tokenExpireAt = 0L;

    public BaiduRecognitionProvider(@Value("${greenthumb.recognition.baidu.api-key:}") String apiKey,
                                    @Value("${greenthumb.recognition.baidu.secret-key:}") String secretKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    @Override
    public String name() {
        return "BAIDU";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !secretKey.isEmpty();
    }

    @Override
    public List<RecognitionCandidate> recognize(byte[] imageBytes, String filename) throws Exception {
        String token = accessToken();

        // ⚠️ 这里只做 base64，不要再手动 URLEncoder。
        // 百度文档要求「base64 后再 urlencode」，但我们是把 image 放进
        // application/x-www-form-urlencoded 的表单体里提交的，
        // Spring 的 FormHttpMessageConverter 已经会自动 urlencode。
        // 手动再编一次 = 编码两遍，百度收到乱码直接返回 216201 image format error。
        String image = Base64.getEncoder().encodeToString(imageBytes);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("image", image);
        form.add("baike_num", "0");

        String body = http.post()
                .uri(PLANT_URL + "?access_token=" + token)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        if (root.has("error_code")) {
            // token 失效时清缓存，下次自动重取
            if (root.path("error_code").asInt() == 110 || root.path("error_code").asInt() == 111) {
                cachedToken = null;
                tokenExpireAt = 0L;
            }
            log.warn("百度返回错误，原始响应：{}", abbreviate(body));
            throw new IllegalStateException("百度识别失败: " + root.path("error_code").asInt()
                    + " " + root.path("error_msg").asText());
        }

        List<RecognitionCandidate> list = new ArrayList<>();
        for (JsonNode item : root.path("result")) {
            String rawName = item.path("name").asText("");
            if (rawName.isEmpty()) {
                continue;
            }
            double score = parseScore(item.path("score").asText("0"));
            list.add(new RecognitionCandidate(rawName, null, score));
        }
        list.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        return list.size() > 3 ? new ArrayList<>(list.subList(0, 3)) : list;
    }

    /* ================= 内部 ================= */

    private synchronized String accessToken() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAt) {
            return cachedToken;
        }
        String url = TOKEN_URL + "?grant_type=client_credentials"
                + "&client_id=" + apiKey
                + "&client_secret=" + secretKey;

        String body = http.post().uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve().body(String.class);

        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        if (root.has("error")) {
            throw new IllegalStateException("百度换取 access_token 失败: " + root.path("error_description").asText());
        }
        String token = root.path("access_token").asText("");
        if (token.isEmpty()) {
            throw new IllegalStateException("百度未返回 access_token");
        }
        long expiresIn = root.path("expires_in").asLong(2592000L); // 默认 30 天
        cachedToken = token;
        // 提前 5 分钟过期，避免边界失效
        tokenExpireAt = now + Math.max(60_000L, (expiresIn - 300) * 1000L);
        log.info("百度 access_token 已获取，有效期 {} 秒", expiresIn);
        return token;
    }

    private static double parseScore(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0d;
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
