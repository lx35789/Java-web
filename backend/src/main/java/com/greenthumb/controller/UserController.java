package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.service.AuthService;
import com.greenthumb.service.UserEnvironmentService;
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
    private final UserEnvironmentService environmentService;

    public UserController(AuthService authService, UserEnvironmentService environmentService) {
        this.authService = authService;
        this.environmentService = environmentService;
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

    /**
     * 读取环境设置。路径与 frontend/src/api/auth.js 的 getEnvironment 对应。
     * 没有记录时返回默认值而不是 404 —— 前端表单需要能渲染。
     */
    @GetMapping("/me/environment")
    public ApiResponse<Map<String, Object>> getEnvironment() {
        return ApiResponse.ok(environmentService.get(CurrentUser.id()));
    }

    /** 保存环境设置。填了城市会自动做地理编码补经纬度 */
    @PutMapping("/me/environment")
    public ApiResponse<Map<String, Object>> updateEnvironment(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(environmentService.update(CurrentUser.id(), body));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
