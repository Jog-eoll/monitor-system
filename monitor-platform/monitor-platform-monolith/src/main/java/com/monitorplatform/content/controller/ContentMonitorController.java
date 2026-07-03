package com.monitorplatform.content.controller;

import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.entity.dto.BoardMonitorListQueryDTO;
import com.monitorplatform.content.entity.dto.BoardTargetRequestDTO;
import com.monitorplatform.content.entity.dto.ContentIdsRequestDTO;
import com.monitorplatform.content.entity.dto.ContentReceiveDTO;
import com.monitorplatform.content.entity.dto.ControlCommandDTO;
import com.monitorplatform.content.entity.dto.DeleteByConditionRequestDTO;
import com.monitorplatform.content.entity.dto.InsertDefaultImageRequestDTO;
import com.monitorplatform.content.entity.vo.ApiResponseVO;
import com.monitorplatform.content.entity.vo.BoardMonitorPageVO;
import com.monitorplatform.content.entity.vo.ContentSummaryVO;
import com.monitorplatform.content.entity.vo.OperationResultVO;
import com.monitorplatform.content.service.ContentMonitorService;
import com.monitorplatform.content.service.ContentRecognitionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/content")
@CrossOrigin(origins = "*")
public class ContentMonitorController {

    @Resource
    private ContentMonitorService contentMonitorService;

    @Resource
    private ContentRecognitionService recognitionService;

    @PostMapping("/board-monitor-list")
    public ApiResponseVO<BoardMonitorPageVO> getBoardMonitorList(@RequestBody(required = false) BoardMonitorListQueryDTO params) {
        int pageNum = params != null && params.getPageNum() != null ? params.getPageNum() : 1;
        int pageSize = params != null && params.getPageSize() != null ? params.getPageSize() : 10;
        String ipKeyword = params != null ? params.getIpKeyword() : null;
        try {
            return ApiResponseVO.success(contentMonitorService.getBoardMonitorList(pageNum, pageSize, ipKeyword));
        } catch (Exception e) {
            log.error("getBoardMonitorList failed", e);
            return ApiResponseVO.error("获取看板监控列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/receive")
    public OperationResultVO<Void> receiveContent(@RequestBody ContentReceiveDTO dto) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            ContentMonitor record = contentMonitorService.receiveContent(dto);
            result.setSuccess(true);
            result.setMessage("内容接收成功");
            result.setContentId(record.getContentId());
            result.setStatus(record.getStatus());
        } catch (Exception e) {
            log.error("receiveContent failed", e);
            result.setSuccess(false);
            result.setMessage("内容接收失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/detail/{contentId}")
    public OperationResultVO<ContentMonitor> getContentDetail(@PathVariable String contentId) {
        OperationResultVO<ContentMonitor> result = new OperationResultVO<>();
        try {
            ContentMonitor record = contentMonitorService.getByContentId(contentId);
            if (record != null) {
                result.setSuccess(true);
                result.setData(record);
            } else {
                result.setSuccess(false);
                result.setMessage("内容不存在");
            }
        } catch (Exception e) {
            log.error("getContentDetail failed", e);
            result.setSuccess(false);
            result.setMessage("查询失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/latest-by-board")
    public OperationResultVO<ContentSummaryVO> getLatestByBoard(@RequestParam String boardIp,
                                                                 @RequestParam(required = false) Integer boardPort) {
        OperationResultVO<ContentSummaryVO> result = new OperationResultVO<>();
        try {
            if (boardIp == null || boardIp.trim().isEmpty()) {
                result.setSuccess(false);
                result.setMessage("boardIp 不能为空");
                return result;
            }
            result.setSuccess(true);
            result.setData(contentMonitorService.getLatestContentByBoard(boardIp, boardPort));
        } catch (Exception e) {
            log.error("getLatestByBoard failed", e);
            result.setSuccess(false);
            result.setMessage("查询失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/list")
    public OperationResultVO<PageDataVO<ContentMonitor>> getContentList(@RequestParam(defaultValue = "1") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        OperationResultVO<PageDataVO<ContentMonitor>> result = new OperationResultVO<>();
        try {
            int offset = (page - 1) * size;
            List<ContentMonitor> list = contentMonitorService.getRecentList(size, offset);
            int total = contentMonitorService.getTotalCount();

            PageDataVO<ContentMonitor> pageData = new PageDataVO<>();
            pageData.setRecords(list);
            pageData.setTotal(total);
            pageData.setCurrent(page);
            pageData.setSize(size);
            pageData.setPages((int) Math.ceil((double) total / size));

            result.setSuccess(true);
            result.setData(pageData);
        } catch (Exception e) {
            log.error("getContentList failed", e);
            result.setSuccess(false);
            result.setMessage("查询失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/violations")
    public OperationResultVO<List<ContentMonitor>> getViolationList(@RequestParam(defaultValue = "50") int limit) {
        OperationResultVO<List<ContentMonitor>> result = new OperationResultVO<>();
        try {
            List<ContentMonitor> list = contentMonitorService.getViolationList(limit);
            result.setSuccess(true);
            result.setData(list);
            result.setTotal(list.size());
        } catch (Exception e) {
            log.error("getViolationList failed", e);
            result.setSuccess(false);
            result.setMessage("查询失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/stop")
    public OperationResultVO<Void> stopDisplay(@Valid @RequestBody ControlCommandDTO dto) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            contentMonitorService.stopDisplay(dto.getContentId(), dto.getOperator());
            result.setSuccess(true);
            result.setMessage("切断成功");
            result.setContentId(dto.getContentId());
        } catch (Exception e) {
            log.error("stopDisplay failed", e);
            result.setSuccess(false);
            result.setMessage("切断失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/resume")
    public OperationResultVO<Void> resumeDisplay(@Valid @RequestBody ControlCommandDTO dto) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            contentMonitorService.resumeDisplay(dto.getContentId(), dto.getOperator());
            result.setSuccess(true);
            result.setMessage("恢复成功");
            result.setContentId(dto.getContentId());
        } catch (Exception e) {
            log.error("resumeDisplay failed", e);
            result.setSuccess(false);
            result.setMessage("恢复失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/reloadWords")
    public OperationResultVO<Void> reloadSensitiveWords() {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            recognitionService.reloadSensitiveWords();
            result.setSuccess(true);
            result.setMessage("敏感词已重载");
        } catch (Exception e) {
            log.error("reloadSensitiveWords failed", e);
            result.setSuccess(false);
            result.setMessage("重载失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/delete/{contentId}")
    public OperationResultVO<Void> deleteContent(@PathVariable String contentId) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            contentMonitorService.deleteContent(contentId);
            result.setSuccess(true);
            result.setMessage("删除成功");
            result.setContentId(contentId);
        } catch (Exception e) {
            log.error("deleteContent failed", e);
            result.setSuccess(false);
            result.setMessage("删除失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/delete-batch")
    public OperationResultVO<Void> deleteBatch(@RequestBody ContentIdsRequestDTO params) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            List<String> contentIds = params != null ? params.getContentIds() : null;
            if (contentIds == null || contentIds.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("contentIds 不能为空");
                return result;
            }
            int count = contentMonitorService.deleteBatch(contentIds);
            result.setSuccess(true);
            result.setMessage("删除成功 " + count + " 条");
            result.setCount(count);
        } catch (Exception e) {
            log.error("deleteBatch failed", e);
            result.setSuccess(false);
            result.setMessage("删除失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/clear-all")
    public OperationResultVO<Void> clearAll() {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            int count = contentMonitorService.clearAll();
            result.setSuccess(true);
            result.setMessage("清空成功，删除 " + count + " 条");
            result.setCount(count);
        } catch (Exception e) {
            log.error("clearAll failed", e);
            result.setSuccess(false);
            result.setMessage("清空失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/delete-by-condition")
    public OperationResultVO<Void> deleteByCondition(@RequestBody DeleteByConditionRequestDTO params) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            String status = params != null ? params.getStatus() : null;
            Integer isViolation = params != null ? params.getIsViolation() : null;
            int count = contentMonitorService.deleteByCondition(status, isViolation);
            result.setSuccess(true);
            result.setMessage("删除成功 " + count + " 条");
            result.setCount(count);
        } catch (Exception e) {
            log.error("deleteByCondition failed", e);
            result.setSuccess(false);
            result.setMessage("删除失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/delete-violation-by-board")
    public OperationResultVO<Void> deleteViolationByBoard(@RequestBody BoardTargetRequestDTO params) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            String boardIp = params != null ? params.getBoardIp() : null;
            Integer boardPort = params != null ? params.getBoardPort() : null;
            if (boardIp == null || boardIp.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("boardIp 不能为空");
                return result;
            }

            List<String> fileNames = contentMonitorService.deleteViolationContentByBoard(boardIp, boardPort);
            result.setSuccess(true);
            result.setMessage("清理成功");
            result.setFileNames(fileNames);
        } catch (Exception e) {
            log.error("deleteViolationByBoard failed", e);
            result.setSuccess(false);
            result.setMessage("清理失败: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/insert-default-image")
    public OperationResultVO<Void> insertDefaultImage(@RequestBody InsertDefaultImageRequestDTO params) {
        OperationResultVO<Void> result = new OperationResultVO<>();
        try {
            String boardIp = params != null ? params.getBoardIp() : null;
            Integer boardPort = params != null ? params.getBoardPort() : null;
            String minioPath = params != null ? params.getMinioPath() : null;
            String operator = (params != null && params.getOperator() != null) ? params.getOperator() : "system";
            if (boardIp == null || boardIp.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("boardIp 不能为空");
                return result;
            }
            contentMonitorService.insertDefaultImageRecord(boardIp, boardPort, minioPath, operator);
            result.setSuccess(true);
            result.setMessage("默认图写入成功");
        } catch (Exception e) {
            log.error("insertDefaultImage failed", e);
            result.setSuccess(false);
            result.setMessage("写入失败: " + e.getMessage());
        }
        return result;
    }

    @Data
    private static class PageDataVO<T> {
        private List<T> records;
        private Integer total;
        private Integer current;
        private Integer size;
        private Integer pages;
    }
}
