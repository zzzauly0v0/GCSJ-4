package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.vo.DictionaryVO;
import com.gcsj.disaster.service.IDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "字典")
@RestController
@RequestMapping("/api/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final IDictionaryService dictionaryService;

    @Operation(summary = "按类型获取字典项")
    @GetMapping
    public Result<List<DictionaryVO>> findByType(@RequestParam String type) {
        return Result.ok(dictionaryService.findByType(type));
    }
}
