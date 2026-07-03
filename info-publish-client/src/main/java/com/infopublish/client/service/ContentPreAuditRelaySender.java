package com.infopublish.client.service;

import com.infopublish.client.entity.dto.ContentAuditRelayPacket;

public interface ContentPreAuditRelaySender {

    boolean send(ContentAuditRelayPacket relayPacket);
}
