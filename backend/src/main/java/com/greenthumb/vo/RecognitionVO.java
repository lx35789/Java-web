package com.greenthumb.vo;

import java.util.List;

/**
 * 识别结果。
 * 字段与 frontend/src/mock/data.js 的 mockRecognition 完全一致。
 * <p>
 * results 为空数组表示降级链全部不可用（通常是还没配 API Key），
 * 前端会提示用户手动选择或跳过这一步。
 */
public record RecognitionVO(
        Long recordId,
        String provider,
        String modelVersion,
        Integer latencyMs,
        /** 首位置信度 < 0.5，或完全没有结果 */
        boolean lowConfidence,
        /** 主通道失败、用了备用通道 */
        boolean fallbackUsed,
        List<Item> results
) {

    public record Item(
            int rank,
            /** 映射到植物库的物种 id；第三方返回的名字不在库里时为 null */
            Long speciesId,
            String commonName,
            String scientificName,
            String emoji,
            double confidence,
            String careSummary
    ) {
    }
}
