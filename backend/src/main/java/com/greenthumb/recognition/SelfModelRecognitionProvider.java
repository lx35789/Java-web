package com.greenthumb.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 自研模型物种识别（本地通道，不需要任何 API Key）。
 * <p>
 * 调用 Python AI 服务的 {@code POST /v1/species/predict}：那边把
 * 「物种 × 病害」29 类的概率按物种求和，得到物种置信度。
 * <p>
 * 为什么值得做：百度、Pl@ntNet 都需要注册并配额，而本地模型已经部署好了，
 * 对训练覆盖的 6 个物种（芦荟 / 绿萝 / 月季 / 姜黄 / 扶桑 / 菊花）可以直接出结果，
 * 既免费又离线可用。
 * <p>
 * 两条重要的降级设计：
 * <ol>
 *   <li><b>置信度低于阈值就返回空列表</b>，让编排器交给下一家（百度/Pl@ntNet）；
 *       都没有时前端引导手动选择 —— 宁可说"没认出来"，也不要给一个错误物种。</li>
 *   <li><b>AI 服务没起时抛异常</b>，由 {@link RecognitionOrchestrator} 兜住并降级，
 *       不影响其他供应商。</li>
 * </ol>
 */
@Component
public class SelfModelRecognitionProvider implements RecognitionProvider {

    private static final Logger log = LoggerFactory.getLogger(SelfModelRecognitionProvider.class);

    /** 低于该置信度视为"没认出来"，交给下一家。经验值：模型在自家 6 物种上通常 >0.8 */
    private static final double MIN_CONFIDENCE = 0.45;

    private final ObjectMapper json = new ObjectMapper();
    private final RestClient http;
    private final String baseUrl;
    private final boolean enabled;

    public SelfModelRecognitionProvider(
            @Value("${greenthumb.ai-service.base-url:http://localhost:8000}") String baseUrl,
            @Value("${greenthumb.ai-service.timeout-ms:6000}") long timeoutMs,
            @Value("${greenthumb.recognition.self-model.enabled:false}") boolean enabled) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.enabled = enabled;

        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1500);
        f.setReadTimeout((int) Math.max(1500L, timeoutMs));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    @Override
    public String name() {
        return "SELF_MODEL";
    }

    @Override
    public boolean isConfigured() {
        return enabled;
    }

    @Override
    public List<RecognitionCandidate> recognize(byte[] imageBytes, String filename) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "photo.jpg" : filename;
            }
        });

        String body = http.post()
                .uri(baseUrl + "/v1/species/predict?top_k=3")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode root = json.readTree(body == null ? "{}" : body);
        if (!root.path("available").asBoolean(false)) {
            log.info("自研物种模型不可用：{}", root.path("reason").asText("未知原因"));
            return List.of();
        }

        List<RecognitionCandidate> list = new ArrayList<>();
        for (JsonNode item : root.path("topK")) {
            double conf = item.path("confidence").asDouble(0d);
            String nameCn = item.path("nameCn").asText("");
            if (nameCn.isEmpty() || conf < MIN_CONFIDENCE) {
                continue;
            }
            // rawName 用中文名：RecognitionService 会用它与 plant_species.common_name 精确匹配
            list.add(new RecognitionCandidate(nameCn, null, conf));
        }

        if (list.isEmpty()) {
            log.info("自研物种模型置信度不足（阈值 {}），交由下一家识别", MIN_CONFIDENCE);
            return List.of();
        }
        list.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        return list;
    }
}
