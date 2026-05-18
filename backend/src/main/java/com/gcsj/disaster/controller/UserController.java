package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateUserDTO;
import com.gcsj.disaster.domain.dto.UpdateUserDTO;
import com.gcsj.disaster.domain.vo.UserVO;
import com.gcsj.disaster.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping
    public Result<PageResult<UserVO>> page(@RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        Pageable pg = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id"));
        return Result.ok(PageResult.from(userService.page(keyword, pg), v -> v));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody CreateUserDTO dto) {
        return Result.ok(userService.create(dto));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserDTO dto) {
        return Result.ok(userService.update(id, dto));
    }

    @Operation(summary = "删除用户 (软删)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteById(id);
        return Result.ok();
    }
}
