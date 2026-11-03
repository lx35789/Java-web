package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.dto.LoginRequest;
import com.greenthumb.dto.RegisterRequest;
import com.greenthumb.service.AuthService;
import com.greenthumb.vo.LoginVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。路径与 frontend/src/api/auth.js 一一对应：
 *   POST /api/v1/auth/login
 *   POST /api/v1/auth/register
 *   POST /api/v1/auth/logout
 *   POST /api/v1/auth/refresh
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<LoginVO> register(@RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /** 前端登出只做本地清理，这里保持幂等即可 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginVO> refresh() {
        return ApiResponse.ok(authService.refresh(CurrentUser.id()));
    }
}
