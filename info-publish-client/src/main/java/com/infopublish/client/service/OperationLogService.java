package com.infopublish.client.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.infopublish.client.entity.OperationLog;

import java.util.List;

public interface OperationLogService {

    String EVENT_UKEY_INSERT = "UKEY_INSERT";
    String EVENT_UKEY_REMOVE = "UKEY_REMOVE";
    String EVENT_CERT_VALIDATE = "CERT_VALIDATE";
    String EVENT_AUTH_SUCCESS = "AUTH_SUCCESS";
    String EVENT_AUTH_FAIL = "AUTH_FAIL";
    String EVENT_CHANNEL_CREATE = "CHANNEL_CREATE";
    String EVENT_CHANNEL_STOP = "CHANNEL_STOP";
    String EVENT_LOGOUT = "LOGOUT";
    String EVENT_DISCONNECT = "DISCONNECT";

    void log(String eventType, String detail, String status);

    void logSuccess(String eventType, String detail);

    void logFail(String eventType, String detail);

    List<OperationLog> getRecentLogs(int limit);

    Page<OperationLog> getLogsByPage(int pageNum, int pageSize);

    List<OperationLog> getLogsByEventType(String eventType, int limit);
}
