package com.monitorplatform.log.controller;

import com.monitorplatform.common.annotation.OperateLog;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.log.dto.DiagnosticEventQueryDTO;
import com.monitorplatform.log.dto.DiagnosticEventReportDTO;
import com.monitorplatform.log.dto.TimelineQueryDTO;
import com.monitorplatform.log.service.DiagnosticEventService;
import com.monitorplatform.log.service.TimelineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/log/event")
@RequiredArgsConstructor
public class DiagnosticEventController {

    private final DiagnosticEventService diagnosticEventService;
    private final TimelineQueryService timelineQueryService;

    @PostMapping("/report")
    @OperateLog(enable = false)
    public Result<?> report(@Validated @RequestBody DiagnosticEventReportDTO dto,
                            @RequestHeader(value = "X-Log-Token", required = false) String token) {
        return Result.data(diagnosticEventService.report(dto, token));
    }

    @PostMapping("/batch-report")
    @OperateLog(enable = false)
    public Result<?> batchReport(@Validated @RequestBody List<DiagnosticEventReportDTO> dtoList,
                                 @RequestHeader(value = "X-Log-Token", required = false) String token) {
        return Result.data(diagnosticEventService.batchReport(dtoList, token));
    }

    @PostMapping("/page")
    @OperateLog(enable = false)
    public Result<?> page(@RequestBody(required = false) DiagnosticEventQueryDTO queryDTO) {
        return Result.data(diagnosticEventService.page(queryDTO));
    }

    @PostMapping("/timeline")
    @OperateLog(enable = false)
    public Result<?> timeline(@RequestBody TimelineQueryDTO queryDTO) {
        return Result.data(timelineQueryService.timeline(queryDTO));
    }
}
