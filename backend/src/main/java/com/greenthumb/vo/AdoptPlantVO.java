package com.greenthumb.vo;

import java.util.List;

/**
 * 「诊断结果 → 加入我的植物」的返回体。
 *
 * @param alreadyAdopted true 表示这次诊断之前已经认领过，本次没有重复建植物（幂等）
 * @param plans          本次生成的养护计划；alreadyAdopted 时为空列表
 */
public record AdoptPlantVO(
        Long plantId,
        String nickname,
        String speciesName,
        String healthStatus,
        Boolean alreadyAdopted,
        List<CarePlanVO> plans
) {
}
