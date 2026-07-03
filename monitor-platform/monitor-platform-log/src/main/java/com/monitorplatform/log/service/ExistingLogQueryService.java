package com.monitorplatform.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.monitorplatform.log.dto.LinkLogQueryDTO;
import com.monitorplatform.log.dto.OperationLogQueryDTO;
import com.monitorplatform.log.dto.PublishAuditLogQueryDTO;
import com.monitorplatform.log.dto.PublishLogQueryDTO;
import com.monitorplatform.log.dto.UnifiedPageResult;

import java.util.Map;

public interface ExistingLogQueryService {

    IPage<Map<String, Object>> pagePublish(PublishLogQueryDTO queryDTO);

    IPage<Map<String, Object>> pageOperation(OperationLogQueryDTO queryDTO);

    IPage<Map<String, Object>> pageLink(LinkLogQueryDTO queryDTO);

    UnifiedPageResult<Map<String, Object>> pagePublishAudit(PublishAuditLogQueryDTO queryDTO);
}
