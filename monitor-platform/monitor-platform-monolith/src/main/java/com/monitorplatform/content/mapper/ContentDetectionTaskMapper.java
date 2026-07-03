package com.monitorplatform.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.content.entity.ContentDetectionTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容检测任务Mapper
 */
@Mapper
public interface ContentDetectionTaskMapper extends BaseMapper<ContentDetectionTask> {
}
