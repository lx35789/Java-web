package com.greenthumb.dto;

/** 注册请求：POST /api/v1/auth/register */
public record RegisterRequest(String username, String password, String nickname, String email) {
}
