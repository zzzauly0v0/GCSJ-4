package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateDictionaryDTO;
import com.gcsj.disaster.domain.vo.DictionaryVO;
import com.gcsj.disaster.service.IDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "字典")
@RestController
@RequestMapping("/api/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final IDictionaryService dictionaryService;

    @Operation(summary = "字典列表 (按 type 筛选; 不传 type 返回全部)")
    @GetMapping
    public Result<List<DictionaryVO>> list(@RequestParam(required = false) String type) {
        if (type == null || type.isBlank()) {
            return Result.ok(dictionaryService.listAll());
        }
        return Result.ok(dictionaryService.findByType(type));
    }

    @Operation(summary = "全部字典类型 (DISTINCT type_code)")
    @GetMapping("/types")
    public Result<List<String>> types() {
        return Result.ok(dictionaryService.listTypes());
    }

    @Operation(summary = "新增字典项")
    @PostMapping
    public Result<DictionaryVO> add(@Valid @RequestBody CreateDictionaryDTO dto) {
        return Result.ok(dictionaryService.add(dto));
    }

    @Operation(summary = "更新字典项")
    @PutMapping("/{id}")
    public Result<DictionaryVO> update(@PathVariable Long id, @Valid @RequestBody CreateDictionaryDTO dto) {
        return Result.ok(dictionaryService.update(id, dto));
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictionaryService.deleteById(id);
        return Result.ok();
    }
}
