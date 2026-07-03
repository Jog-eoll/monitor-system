package com.infopublish.client.service;

public interface ClientAuthService {

    boolean authenticateWithControlPlatform(String ukeyPath);

    byte[] signEnvelopeWithControlPlatform(String ukeyPath, byte[] data);

    byte[] verifyEnvelopeWithControlPlatform(String ukeyPath, byte[] signedEnvelope);

    boolean isAuthenticated();

    boolean isConfigReady();

    void clearAuthentication();

    void setMonitorPlatformUrl(String url);

    void setServerId(String serverId);

    void setServerCerPath(String path);

    void setClientCerPath(String path);

    void setAuthId(String authId);

    void setPassword(String password);

    void setMockMode(boolean mockMode);

    String getMonitorPlatformUrl();

    String getServerId();

    String getServerCerPath();

    String getClientCerPath();

    String getAuthId();

    String getPassword();

    boolean isMockMode();
}
