package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.PageResult;
import com.greenthumb.service.SpeciesService;
import com.greenthumb.vo.SpeciesVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 植物种类库接口。路径与 frontend/src/api/species.js 对应：
 *   GET /api/v1/species
 *   GET /api/v1/species/{id}
 * <p>
 * 注意：这两个接口在拦截器保护范围内（/api/**），所以需要登录。
 * 植物库数据本身不涉及隐私，但统一要求登录可以让拦截规则保持简单。
 */
@RestController
@RequestMapping("/api/v1/species")
public class SpeciesController {

    private final SpeciesService speciesService;

    public SpeciesController(SpeciesService speciesService) {
        this.speciesService = speciesService;
    }

    @GetMapping
    public ApiResponse<PageResult<SpeciesVO>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "light", required = false) String light,
            @RequestParam(name = "difficulty", required = false) String difficulty,
            @RequestParam(name = "toxic", required = false) String toxic,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "20") long size) {
        return ApiResponse.ok(speciesService.list(keyword, light, difficulty, toxic, page, size));
    }

    /** 路径变量限制为纯数字：/species/abc 应当 404，而不是抛类型转换异常变成 500 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<SpeciesVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(speciesService.detail(id));
    }
}
