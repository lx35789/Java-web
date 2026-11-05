package com.greenthumb.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物种中文名 → 模型类别码前缀。
 * <p>
 * 必须与 {@code ai-service/models/labels.json} 里的 {@code species} 字段一致：
 * 模型类别码形如 {@code Aloe_Rust}、{@code Rose_Powdery_Mildew}，前缀就是物种 key。
 * <p>
 * 这个映射决定「是否使用物种专用模型」：
 * 传了 key 且该物种有专用模型（目前只有 aloe），AI 服务会路由到专用模型；
 * 不传则一律用通用 29 类模型 —— 芦荟上准确率会从 ~75% 掉到 ~8%，
 * 所以凡是能确定物种的场景都必须传。
 */
public final class SpeciesKeys {

    private static final Map<String, String> PREFIX = new LinkedHashMap<>();

    static {
        PREFIX.put("芦荟", "Aloe");
        PREFIX.put("绿萝", "Money_Plant");
        PREFIX.put("月季", "Rose");
        PREFIX.put("姜黄", "Turmeric");
        PREFIX.put("扶桑", "Hibiscus");
        PREFIX.put("菊花", "Chrysanthemum");
    }

    private SpeciesKeys() {
    }

    /**
     * 按中文名查模型 species key；查不到返回 null（调用方应回退通用模型）。
     *
     * @param commonName 植物库里的中文名，如「芦荟」
     */
    public static String keyOf(String commonName) {
        if (commonName == null || commonName.isBlank()) {
            return null;
        }
        String name = commonName.trim();
        String exact = PREFIX.get(name);
        if (exact != null) {
            return exact;
        }
        // 容错：植物库里可能写成「芦荟（库拉索）」这种带后缀的形式
        for (Map.Entry<String, String> e : PREFIX.entrySet()) {
            if (name.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }
}
