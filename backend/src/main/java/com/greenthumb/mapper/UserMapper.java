package com.greenthumb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenthumb.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * user 表的数据访问层。
 * BaseMapper 已经提供 insert / selectById / selectList / updateById / deleteById 等常用方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
