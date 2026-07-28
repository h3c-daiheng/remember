package com.zhiyi.memory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Memory 操作追踪日志数据访问层
 */
@Mapper
public interface MemoryOperationLogMapper extends BaseMapper<MemoryOperationLogEntity> {
}
