package com.infopublish.client.service;

import java.util.Map;

public interface MonitorPlatformClient {

    Map<String, Object> validateCertificate(String certSerialNo, String certificateContent);

    boolean notifyClientAuthenticated(String certSerialNo, String authToken);

    boolean notifyClientDisconnected(String certSerialNo, String reason);

    Map<String, Object> registerUkey(String ukeyId, String certificate);

    String getPlatformUrl();

    String getClientId();

    void setPlatformUrl(String platformUrl);

    void setClientId(String clientId);

    void sendHeartbeat(String certSerialNo);
}
