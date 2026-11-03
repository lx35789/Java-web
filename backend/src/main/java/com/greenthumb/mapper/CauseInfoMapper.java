package com.greenthumb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenthumb.entity.CauseInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/** 症状病因字典 */
@Mapper
public interface CauseInfoMapper extends BaseMapper<CauseInfoEntity> {
}
