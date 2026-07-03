package com.monitorplatform.forward.service;

import lombok.Data;

@Data
public class IpChangeCallbackResult {

    private boolean deviceUpdated;

    private int linkNodesUpdated;

    private boolean success;

    public static IpChangeCallbackResult skipped() {
        IpChangeCallbackResult r = new IpChangeCallbackResult();
        r.setSuccess(true);
        return r;
    }

    public static IpChangeCallbackResult failure(boolean deviceUpdated, int linkNodesUpdated) {
        IpChangeCallbackResult r = new IpChangeCallbackResult();
        r.setDeviceUpdated(deviceUpdated);
        r.setLinkNodesUpdated(linkNodesUpdated);
        r.setSuccess(false);
        return r;
    }

    public static IpChangeCallbackResult success(boolean deviceUpdated, int linkNodesUpdated) {
        IpChangeCallbackResult r = new IpChangeCallbackResult();
        r.setDeviceUpdated(deviceUpdated);
        r.setLinkNodesUpdated(linkNodesUpdated);
        r.setSuccess(true);
        return r;
    }
}
