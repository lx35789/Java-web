package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.common.JwtUtil;
import com.greenthumb.dto.LoginRequest;
import com.greenthumb.dto.RegisterRequest;
import com.greenthumb.entity.UserEntity;
import com.greenthumb.mapper.UserMapper;
import com.greenthumb.vo.LoginVO;
import com.greenthumb.vo.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 认证服务。
 * <p>
 * 数据源：MySQL 的 user 表（见 db/schema.sql）。
 * 初始账号需要在数据库里自行创建，或者调用 POST /api/v1/auth/register 注册。
 */
@Service
public class AuthService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    /* ================= 对外方法 ================= */

    /** 用户名以 admin 开头 → 管理员（与前端 Mock 的约定保持一致） */
    private static String roleOf(String username) {
        return username.toLowerCase().startsWith("admin") ? "ADMIN" : "USER";
    }

    public LoginVO login(LoginRequest req) {
        String username = trim(req == null ? null : req.username());
        String password = req == null ? null : req.password();
        if (username.isEmpty() || password == null || password.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名和密码都不能为空");
        }
        UserEntity user = findByUsername(username);
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 记录最后登录时间（只更新这一个字段）
        UserEntity patch = new UserEntity();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);

        return issue(user);
    }

    /**
     * 登录 / 注册一体化。
     * <p>
     * 规则（刻意如此，避免"输错密码被悄悄注册成新号"这种困惑）：
     * - 用户名不存在 → 用本次输入的密码自动注册，并直接登录
     * - 用户名已存在 → 必须密码正确，否则明确报"用户名或密码错误"
     * <p>
     * 移动端只有一个入口，用户不必先想"我注册过没有"。
     */
    @Transactional
    public LoginVO loginOrRegister(LoginRequest req) {
        String username = trim(req == null ? null : req.username());
        String password = req == null ? null : req.password();
        if (username.isEmpty() || password == null || password.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名和密码都不能为空");
        }

        UserEntity user = findByUsername(username);

        // ---------- 情况 1：用户不存在 → 自动注册 ----------
        if (user == null) {
            if (username.length() < 3 || username.length() > 32) {
                throw new BizException(ErrorCode.PARAM_INVALID, "用户名长度需在 3–32 个字符之间");
            }
            if (password.length() < 6) {
                throw new BizException(ErrorCode.PARAM_INVALID, "密码至少 6 位");
            }
            LocalDateTime now = LocalDateTime.now();
            UserEntity created = new UserEntity();
            created.setUsername(username);
            created.setPasswordHash(encoder.encode(password));
            created.setNickname(username);
            created.setRole(roleOf(username));
            created.setStatus(1);
            created.setDeleted(0);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            created.setLastLoginAt(now);
            userMapper.insert(created);
            return issue(created);
        }

        // ---------- 情况 2：用户已存在 → 校验密码 ----------
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        UserEntity patch = new UserEntity();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);
        return issue(user);
    }

    @Transactional
    public LoginVO register(RegisterRequest req) {
        String username = trim(req == null ? null : req.username());
        String password = req == null ? null : req.password();
        if (username.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 32) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名长度需在 3–32 个字符之间");
        }
        if (password == null || password.length() < 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码至少 6 位");
        }
        if (findByUsername(username) != null) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        String nickname = trim(req.nickname());
        if (nickname.isEmpty()) {
            nickname = username;
        }
        String email = trim(req.email());

        LocalDateTime now = LocalDateTime.now();
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email.isEmpty() ? null : email);
        user.setRole(roleOf(username));
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        return issue(user);
    }

    public LoginVO refresh(Long userId) {
        return issue(requireUser(userId));
    }

    public UserVO getById(Long userId) {
        return toVO(requireUser(userId));
    }

    public UserVO updateProfile(Long userId, String nickname, String avatarUrl, String email) {
        UserEntity exist = requireUser(userId);
        UserEntity patch = new UserEntity();
        patch.setId(exist.getId());
        if (nickname != null && !nickname.isBlank()) {
            patch.setNickname(nickname.trim());
        }
        if (avatarUrl != null) {
            patch.setAvatarUrl(avatarUrl);
        }
        if (email != null && !email.isBlank()) {
            patch.setEmail(email.trim());
        }
        patch.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(patch);
        return toVO(requireUser(userId));
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserEntity user = requireUser(userId);
        if (oldPassword == null || !encoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码至少 6 位");
        }
        UserEntity patch = new UserEntity();
        patch.setId(user.getId());
        patch.setPasswordHash(encoder.encode(newPassword));
        patch.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    /* ================= 内部 ================= */

    private UserEntity findByUsername(String username) {
        List<UserEntity> list = userMapper.selectList(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        return list.isEmpty() ? null : list.get(0);
    }

    private UserEntity requireUser(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private LoginVO issue(UserEntity user) {
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        return new LoginVO(token, toVO(user));
    }

    private UserVO toVO(UserEntity u) {
        String createdAt = u.getCreatedAt() == null ? null : u.getCreatedAt().format(ISO);
        return new UserVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(),
                u.getEmail(), u.getRole(), createdAt);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
