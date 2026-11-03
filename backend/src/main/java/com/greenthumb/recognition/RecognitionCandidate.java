package com.greenthumb.recognition;

/**
 * 一个识别候选（来自第三方 API 或自研模型的原始输出）。
 * <p>
 * 注意 rawName 是**原始名称**，不一定能在我们的 50 种植物库里找到；
 * 映射到库内物种由 RecognitionService 负责。
 */
public record RecognitionCandidate(
        String rawName,
        String scientificName,
        double confidence
) {
}
