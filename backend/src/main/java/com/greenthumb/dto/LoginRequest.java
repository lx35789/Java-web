package com.greenthumb.dto;

/** 登录请求：POST /api/v1/auth/login */
public record LoginRequest(String username, String password) {
}
