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
 * Pl@ntNet 识别 API（备选通道）。
 * <p>
 * 文档：https://my.plantnet.org/doc/openapi
 * 免费额度：注册后每天 500 次。
 * <p>
 * 配置：
 * <pre>
 * greenthumb:
 *   recognition:
 *     plantnet:
 *       api-key: 你的KEY
 * </pre>
 */
@Component
public class PlantNetRecognitionProvider implements RecognitionProvider {

    private static final Logger log = LoggerFactory.getLogger(PlantNetRecognitionProvider.class);
    private static final String URL = "https://my-api.plantnet.org/v2/identify/all";

    private final RestClient http = buildClient();

    /** 同百度：RestClient 默认无超时，不设的话外部接口卡住会把请求永远挂住 */
    private static RestClient buildClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(12000);
        return RestClient.builder().requestFactory(f).build();
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;

    public PlantNetRecognitionProvider(@Value("${greenthumb.recognition.plantnet.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public String name() {
        return "PLANTNET";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public List<RecognitionCandidate> recognize(byte[] imageBytes, String filename) throws Exception {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("images", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "photo.jpg" : filename;
            }
        });
        form.add("organs", "leaf");

        String body = http.post()
                .uri(URL + "?api-key=" + apiKey + "&lang=zh")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        List<RecognitionCandidate> list = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            JsonNode species = item.path("species");
            String sci = species.path("scientificNameWithoutAuthor").asText("");
            String common = "";
            JsonNode names = species.path("commonNames");
            if (names.isArray() && names.size() > 0) {
                common = names.get(0).asText("");
            }
            String rawName = common.isEmpty() ? sci : common;
            if (rawName.isEmpty()) {
                continue;
            }
            list.add(new RecognitionCandidate(rawName, sci, item.path("score").asDouble(0d)));
        }
        list.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        return list.size() > 3 ? new ArrayList<>(list.subList(0, 3)) : list;
    }
}
