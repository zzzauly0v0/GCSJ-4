package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateRoleDTO;
import com.gcsj.disaster.domain.vo.RoleVO;
import com.gcsj.disaster.service.IRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final IRoleService roleService;

    @Operation(summary = "全部角色")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.listAll());
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody CreateRoleDTO dto) {
        return Result.ok(roleService.create(dto));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody CreateRoleDTO dto) {
        return Result.ok(roleService.update(id, dto));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteById(id);
        return Result.ok();
    }
}
