package com.monitorplatform.log.service;

import com.monitorplatform.log.dto.TimelineQueryDTO;
import com.monitorplatform.log.vo.TimelineEventVO;

import java.util.List;

public interface TimelineQueryService {

    List<TimelineEventVO> timeline(TimelineQueryDTO queryDTO);
}
