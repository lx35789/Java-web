package com.greenthumb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 相关配置：
 * 1) CORS —— 开发期允许 5173(dev) / 4173(preview)
 * 2) /files/** —— 映射到本地上传目录，供前端直接 <img src="/files/xxx.jpg">
 * <p>
 * 正常情况下前端走 Vite 代理（vite.config.js 里 /api 和 /files 都代理到 8080），
 * 不会触发跨域；这里的 CORS 是为了直连后端调试时也能用。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${greenthumb.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${greenthumb.files-url-prefix:/files}")
    private String filesUrlPrefix;

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /** 除登录 / 注册 / 登出 / ping 外，/api/** 一律要求携带有效 JWT */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/ping",
                        "/api/v1/auth/login",
                        "/api/v1/auth/login-or-register",
                        "/api/v1/auth/register",
                        "/api/v1/auth/logout");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:*",     // Vite dev / preview（带端口）
                        "http://127.0.0.1:*",
                        "http://localhost",       // Capacitor Android，androidScheme=http 时来源无端口
                        "https://localhost",      // Capacitor Android，androidScheme=https
                        "capacitor://localhost",  // Capacitor iOS
                        "http://192.168.*:*",     // 手机浏览器直连局域网后端（调试）
                        "https://*.ngrok-free.app", // 内网穿透（ngrok 免费域名）
                        "https://*"               // 其它公网穿透/部署域名（生产应收窄）
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(TraceIdFilter.HEADER)
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(filesUrlPrefix + "/**")
                .addResourceLocations(location);
    }
}
