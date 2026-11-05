package com.greenthumb.service;

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
 * Pl@ntNet 病害识别 API 客户端。
 * <p>
 * 文档：https://my.plantnet.org/doc/api/diseases
 * <ul>
 *   <li>{@code POST /v2/diseases/identify} → 返回 EPPO 编码 + 置信度 + 英文/拉丁描述</li>
 *   <li>覆盖范围有限（官方原话：only a limited list of species and pathologies）</li>
 *   <li>与物种识别共用额度，每次 1 credit，单次约 10–12 秒</li>
 * </ul>
 * <p>
 * 用途定位：**自训模型给不出结论时的第二意见**。它返回的是 EPPO 编码和拉丁名，
 * 我们知识库的 29 条病害没有填 EPPO 编码，所以这里只取"候选 + 置信度"，
 * 由大模型翻成中文并判断是否成立，而不是直接映射到知识库。
 * <p>
 * 与 {@link com.greenthumb.recognition.PlantNetRecognitionProvider}（物种识别）是两套接口，不要混用。
 */
@Component
public class PlantNetDiseaseClient {

    private static final Logger log = LoggerFactory.getLogger(PlantNetDiseaseClient.class);
    private static final String URL = "https://my-api.plantnet.org/v2/diseases/identify";

    /** 一条病害候选 */
    public record Hit(String code, double score, String description) {
        public String label() {
            String d = description == null ? "" : description.trim();
            return d.isEmpty() ? code : d + " (" + code + ")";
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final RestClient http;
    private final String apiKey;

    public PlantNetDiseaseClient(@Value("${greenthumb.recognition.plantnet.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        // 实测 ~12s，留余量；失败就当没有第二意见，不影响主流程
        f.setReadTimeout(25000);
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * 识别病害。
     *
     * @return 按置信度降序的候选；未配置 / 失败 / 无结果时返回空列表（不抛异常）
     */
    public List<Hit> identify(byte[] imageBytes, String filename) {
        if (!isConfigured() || imageBytes == null || imageBytes.length == 0) {
            return List.of();
        }
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("images", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename == null || filename.isBlank() ? "photo.jpg" : filename;
                }
            });

            String body = http.post()
                    .uri(URL + "?nb-results=3&api-key=" + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = json.readTree(body == null ? "{}" : body);
            List<Hit> list = new ArrayList<>();
            for (JsonNode n : root.path("results")) {
                String code = n.path("name").asText("");
                if (code.isEmpty()) {
                    continue;
                }
                list.add(new Hit(code, n.path("score").asDouble(0d), n.path("description").asText("")));
            }
            list.sort((a, b) -> Double.compare(b.score(), a.score()));
            log.info("Pl@ntNet 病害识别返回 {} 条候选，Top1={} {}",
                    list.size(), list.isEmpty() ? "-" : list.get(0).code(),
                    list.isEmpty() ? "" : String.format("%.2f", list.get(0).score()));
            return list;
        } catch (Exception e) {
            log.warn("Pl@ntNet 病害识别失败（不影响主流程）：{}", e.getMessage());
            return List.of();
        }
    }
}
