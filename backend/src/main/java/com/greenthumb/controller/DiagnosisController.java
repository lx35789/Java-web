package com.greenthumb.controller;

import com.greenthumb.common.ApiResponse;
import com.greenthumb.common.CurrentUser;
import com.greenthumb.common.PageResult;
import com.greenthumb.dto.DiagnosisRequest;
import com.greenthumb.dto.DiagnosisStatusRequest;
import com.greenthumb.dto.SymptomDiagnosisRequest;
import com.greenthumb.service.AiClientService;
import com.greenthumb.service.DiagnosisService;
import com.greenthumb.vo.DiagnosisSummaryVO;
import com.greenthumb.vo.DiseaseDiagnosisVO;
import com.greenthumb.vo.SymptomDiagnosisVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能诊断。路径与 frontend/src/api/diagnosis.js 一一对应：
 * <pre>
 *   POST /api/v1/diagnoses/disease        病害识别（病理性，调 Python AI 服务）
 *   POST /api/v1/diagnoses/symptom        症状判因（生理性，走规则库）
 *   GET  /api/v1/diagnoses                历史列表（分页）
 *   GET  /api/v1/diagnoses/{id}           详情
 *   PUT  /api/v1/diagnoses/{id}/status    标记处理状态
 *   GET  /api/v1/diagnoses/model-status   模型是否就绪（前端可提前提示）
 * </pre>
 * 路径变量一律限制为纯数字，否则 /diagnoses/model-status 会被当成 id 匹配。
 */
@RestController
@RequestMapping("/api/v1/diagnoses")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;
    private final AiClientService aiClient;
    private final com.greenthumb.service.AiDiagnosisService aiDiagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService, AiClientService aiClient,
                               com.greenthumb.service.AiDiagnosisService aiDiagnosisService) {
        this.diagnosisService = diagnosisService;
        this.aiClient = aiClient;
        this.aiDiagnosisService = aiDiagnosisService;
    }

    /**
     * AI 兜底诊断：自训模型认不了时调多模态大模型。
     * 与 /disease 的区别：/disease 只走封闭集 CNN；本接口走三层降级，能覆盖数据外的病害。
     */
    @PostMapping("/ai")
    public ApiResponse<com.greenthumb.vo.AiDiagnosisVO> ai(
            @RequestBody(required = false) com.greenthumb.dto.AiDiagnosisRequest request) {
        return ApiResponse.ok(aiDiagnosisService.diagnose(CurrentUser.id(), request));
    }

    @PostMapping("/disease")
    public ApiResponse<DiseaseDiagnosisVO> disease(@RequestBody(required = false) DiagnosisRequest request) {
        return ApiResponse.ok(diagnosisService.diagnoseDisease(CurrentUser.id(), request));
    }

    @PostMapping("/symptom")
    public ApiResponse<SymptomDiagnosisVO> symptom(@RequestBody(required = false) SymptomDiagnosisRequest request) {
        return ApiResponse.ok(diagnosisService.diagnoseSymptom(CurrentUser.id(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<DiagnosisSummaryVO>> list(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size,
            @RequestParam(value = "diagnosisType", required = false) String diagnosisType) {
        return ApiResponse.ok(diagnosisService.list(CurrentUser.id(), diagnosisType, page, size));
    }

    /**
     * 模型状态 + **实际覆盖的物种**。
     *
     * <p>coveredSpecies 是从 AI 模型的 labels.json 反推出来的中文名，
     * 是「哪些植物能做病害识别」的唯一真相。
     *
     * <p>以前前端自己维护一份硬编码清单（只有 6 种），模型扩到 9 种后没同步，
     * 结果仙人掌/虎皮兰/吊兰在界面上直接显示「暂无病害识别数据」并跳过，
     * 压根不会调病害接口 —— 用户侧表现就是「放了仙人掌照片不识别病害」。
     * 现在改成前端从这里读，模型换了下一次请求就同步。
     */
    @GetMapping("/model-status")
    public ApiResponse<Map<String, Object>> modelStatus() {
        AiClientService.Health h = aiClient.health();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", h.available());
        data.put("reason", h.reason());
        data.put("numClasses", h.numClasses());

        List<String> cn = new ArrayList<>();
        for (String key : h.species()) {
            String name = com.greenthumb.common.SpeciesKeys.commonNameOf(key);
            if (name != null) {
                cn.add(name);
            }
        }
        data.put("coveredSpecies", cn);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<DiseaseDiagnosisVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(diagnosisService.get(CurrentUser.id(), id));
    }

    @PutMapping("/{id:\\d+}/status")
    public ApiResponse<DiagnosisSummaryVO> updateStatus(@PathVariable("id") Long id,
                                                        @RequestBody(required = false) DiagnosisStatusRequest request) {
        return ApiResponse.ok(diagnosisService.updateStatus(
                CurrentUser.id(), id, request == null ? null : request.status()));
    }
}
