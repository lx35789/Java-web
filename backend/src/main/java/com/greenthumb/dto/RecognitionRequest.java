package com.greenthumb.dto;

/** 识别请求：POST /api/v1/recognitions */
public record RecognitionRequest(Long photoId) {
}
