package com.greenthumb.dto;

/**
 * 把一次诊断结果「认领」成一盆我的植物：POST /api/v1/my-plants/from-diagnosis
 *
 * <p>为什么把 nameCn / category 从诊断结果再传回来，而不是后端重新查一次知识库：
 * 模型经常比知识库新（模型有 44 类，disease_info 里未必每条都建了档），
 * 只靠 disease_info_id 会出现「诊断页显示了病名，加进植物后却变成 null」。
 * 这些字段只用于落 note 与计划说明，属于展示文案，不参与任何判断。
 */
public record AdoptDiagnosisRequest(
        /** disease_record.id，诊断识别时就已落库 */
        Long recordId,
        /** 第 1 步识别出的物种；为空时用 speciesName 兜底 */
        Long speciesId,
        String speciesName,
        /** 病灶名，如「绿萝·叶斑病」或「绿萝·健康」 */
        String diseaseLabel,
        /** FUNGAL / BACTERIAL / PEST / PHYSIOLOGICAL，用于决定浇水修正 */
        String diseaseCategory,
        String nickname,
        String location,
        String lightLevel,
        String potSize,
        String potMaterial,
        String soilType
) {
}
