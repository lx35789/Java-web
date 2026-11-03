package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.AuthService;
import com.greenthumb.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户接口。路径与 frontend/src/api/auth.js 一一对应。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.ok(authService.getById(CurrentUser.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserVO> updateMe(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.updateProfile(
                CurrentUser.id(),
                str(body.get("nickname")),
                str(body.get("avatarUrl")),
                str(body.get("email"))));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, Object> body) {
        authService.changePassword(CurrentUser.id(),
                str(body.get("oldPassword")),
                str(body.get("newPassword")));
        return ApiResponse.ok();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
