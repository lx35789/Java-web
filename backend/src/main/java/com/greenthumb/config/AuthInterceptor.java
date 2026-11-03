package com.greenthumb.config;

import com.greenthumb.common.BizException;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.common.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 Authorization: Bearer &lt;token&gt;，把用户信息放进 CurrentUser。
 * 放行路径在 WebConfig.addInterceptors 里配置（登录、注册、ping）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 只拦截真正映射到 Controller 的请求。
        // 未实现的接口会落到静态资源处理器，交给它正常返回 404 ——
        // 这样前端「混合模式」才能识别 404 并回退 Mock；
        // 否则未实现的接口会被这里拦成 1002，前端会误判为登录过期而反复踢下线。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(PREFIX.length()).trim());
            CurrentUser.set(new CurrentUser.Info(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class)));
            return true;
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
