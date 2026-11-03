package com.greenthumb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenthumb.entity.DiseaseInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/** 病害知识库 */
@Mapper
public interface DiseaseInfoMapper extends BaseMapper<DiseaseInfoEntity> {
}
