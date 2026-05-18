package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.LoginDTO;
import com.gcsj.disaster.domain.vo.LoginVO;
import com.gcsj.disaster.domain.vo.UserVO;
import com.gcsj.disaster.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 Controller — 登录 / 当前用户 (公开 + 鉴权混合)
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.ok(userService.currentProfile());
    }

    @Operation(summary = "登出 (前端清 token 即可，此处仅占位)")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
