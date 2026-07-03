package com.monitorplatform.content.service;

import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.entity.dto.ContentReceiveDTO;
import com.monitorplatform.content.entity.dto.RecognitionResultDTO;
import com.monitorplatform.content.entity.vo.BoardMonitorPageVO;
import com.monitorplatform.content.entity.vo.ContentSummaryVO;

import java.util.List;

public interface ContentMonitorService {

    ContentMonitor receiveContent(ContentReceiveDTO dto);

    ContentMonitor getByContentId(String contentId);

    List<ContentMonitor> getRecentList(int limit, int offset);

    int getTotalCount();

    List<ContentMonitor> getViolationList(int limit);

    void stopDisplay(String contentId, String operator);

    void resumeDisplay(String contentId, String operator);

    void updateRecognitionResult(String contentId, RecognitionResultDTO result);

    void deleteContent(String contentId);

    int deleteBatch(List<String> contentIds);

    int clearAll();

    int deleteByCondition(String status, Integer isViolation);

    BoardMonitorPageVO getBoardMonitorList(int pageNum, int pageSize, String ipKeyword);

    ContentSummaryVO getLatestContentByBoard(String boardIp, Integer boardPort);

    List<String> deleteViolationContentByBoard(String boardIp, Integer boardPort);

    void insertDefaultImageRecord(String boardIp, Integer boardPort, String minioPath, String operator);
}
