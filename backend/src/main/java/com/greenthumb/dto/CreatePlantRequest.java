package com.greenthumb.dto;

/** 添加植物请求：POST /api/v1/my-plants */
public record CreatePlantRequest(
        Long speciesId,
        String nickname,
        String location,
        String lightLevel,
        String potSize,
        String potMaterial,
        String soilType,
        /** yyyy-MM-dd */
        String acquiredDate,
        String note
) {
}
