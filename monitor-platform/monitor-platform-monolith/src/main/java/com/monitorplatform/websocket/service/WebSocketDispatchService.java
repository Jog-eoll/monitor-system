package com.monitorplatform.websocket.service;

import com.monitorplatform.common.websocket.WebSocketPushMessage;

/**
 * Dispatch Redis push messages to STOMP topics.
 */
public interface WebSocketDispatchService {

    void dispatch(WebSocketPushMessage message);
}
