package com.greenthumb.dto;

import java.util.List;
import java.util.Map;

/**
 * 症状诊断请求：POST /api/v1/diagnoses/symptom
 * <p>
 * 注意：浇水频率、光照、湿度、温度、季节这些上下文，前端**不用传**，
 * 后端会从养护日志 / 植物档案 / 环境设置里推算（见 docs/03-症状诊断规则库.md）。
 * 前端只负责把"看得见的现象"告诉后端。
 *
 * @param userPlantId  可选，但强烈建议传——不传就没有档案上下文可算
 * @param photoId      可选
 * @param symptomCodes 症状码，如 YELLOW_LEAF / LEAF_TIP_BURN / BASE_ROT
 * @param context      附加观察，如 {"leafPosition":"OLD"}
 */
public record SymptomDiagnosisRequest(Long userPlantId,
                                      Long photoId,
                                      List<String> symptomCodes,
                                      Map<String, Object> context) {
}
