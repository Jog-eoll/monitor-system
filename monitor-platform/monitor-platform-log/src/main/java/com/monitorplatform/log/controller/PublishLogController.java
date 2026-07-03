package com.monitorplatform.log.controller;

import com.monitorplatform.common.annotation.OperateLog;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.log.dto.PublishLogQueryDTO;
import com.monitorplatform.log.service.ExistingLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/log/publish")
@RequiredArgsConstructor
public class PublishLogController {

    private final ExistingLogQueryService existingLogQueryService;

    @PostMapping("/page")
    @OperateLog(enable = false)
    public Result<?> page(@RequestBody(required = false) PublishLogQueryDTO queryDTO) {
        return Result.data(existingLogQueryService.pagePublish(queryDTO));
    }
}
