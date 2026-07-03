package com.monitorplatform.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.monitorplatform.log.dto.DiagnosticEventQueryDTO;
import com.monitorplatform.log.dto.DiagnosticEventReportDTO;
import com.monitorplatform.log.entity.DiagnosticEventLog;
import com.monitorplatform.log.vo.ReportResultVO;

import java.util.List;

public interface DiagnosticEventService {

    ReportResultVO report(DiagnosticEventReportDTO dto, String reportToken);

    List<ReportResultVO> batchReport(List<DiagnosticEventReportDTO> dtoList, String reportToken);

    IPage<DiagnosticEventLog> page(DiagnosticEventQueryDTO queryDTO);

    int cleanupExpired();
}
