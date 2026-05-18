package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.vo.OrganizationVO;
import com.gcsj.disaster.service.IOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "组织机构")
@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrgController {

    private final IOrgService orgService;

    @Operation(summary = "组织树")
    @GetMapping("/tree")
    public Result<List<OrganizationVO>> tree() {
        return Result.ok(orgService.listAsTree());
    }
}
