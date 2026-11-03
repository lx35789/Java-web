package com.greenthumb.vo;

/** 照片上传返回体。字段与 frontend/src/mock/index.js 的 POST /photos 一致。 */
public record PhotoVO(
        Long id,
        String url,
        String thumbUrl,
        Integer width,
        Integer height,
        Integer fileSize,
        String photoType
) {
}
