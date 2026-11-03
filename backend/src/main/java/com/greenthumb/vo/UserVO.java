package com.greenthumb.vo;

/**
 * 返回给前端的用户对象。
 * 字段与 frontend/src/mock/data.js 的 mockUser 完全一致，页面代码零改动。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        String role,
        String createdAt
) {
}
