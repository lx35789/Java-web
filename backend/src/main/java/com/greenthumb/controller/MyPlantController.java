package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.dto.CreatePlantRequest;
import com.greenthumb.service.MyPlantService;
import com.greenthumb.vo.MyPlantDetailVO;
import com.greenthumb.vo.MyPlantVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的植物接口。路径与 frontend/src/api/myPlant.js 对应：
 *   GET    /api/v1/my-plants
 *   GET    /api/v1/my-plants/{id}
 *   POST   /api/v1/my-plants
 * <p>
 * 所有接口的 userId 都取自 JWT（CurrentUser），前端传什么都改变不了查询范围。
 */
@RestController
@RequestMapping("/api/v1/my-plants")
public class MyPlantController {

    private final MyPlantService myPlantService;

    public MyPlantController(MyPlantService myPlantService) {
        this.myPlantService = myPlantService;
    }

    @GetMapping
    public ApiResponse<List<MyPlantVO>> list(
            @RequestParam(name = "healthStatus", required = false) String healthStatus) {
        return ApiResponse.ok(myPlantService.list(CurrentUser.id(), healthStatus));
    }

    /**
     * 详情。
     * <p>
     * ★ 路径变量必须限制为纯数字「{@code {id:\d+}}」：
     * 否则 /my-plants/natural-language 也会被当成 id 匹配到这里，
     * 后端返回 405（路径存在但方法不对），而前端混合模式只在 404 时回退 Mock，
     * 结果就是「自然语言添加」功能直接报错。
     * 限制成数字后，未实现的子路径会正常返回 404，从而正确回退到 Mock。
     */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<MyPlantDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(myPlantService.detail(CurrentUser.id(), id));
    }

    @PostMapping
    public ApiResponse<MyPlantVO> create(@RequestBody CreatePlantRequest request) {
        return ApiResponse.ok(myPlantService.create(CurrentUser.id(), request));
    }
}
