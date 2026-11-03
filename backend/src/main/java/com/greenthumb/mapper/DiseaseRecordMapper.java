package com.greenthumb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenthumb.entity.DiseaseRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/** 病害/症状诊断记录 */
@Mapper
public interface DiseaseRecordMapper extends BaseMapper<DiseaseRecordEntity> {
}
