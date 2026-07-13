package com.publishgateway.udpproxy.service;

public class LegacyCapturedContentReportRequest {

    private final String ruleId;
    private final Long chainId;
    private final byte[] data;
    private final String sourceIp;
    private final String manufacturer;
    private final String boardIp;
    private final Integer boardPort;

    public LegacyCapturedContentReportRequest(String ruleId, Long chainId, byte[] data,
                                              String sourceIp, String manufacturer,
                                              String boardIp, Integer boardPort) {
        this.ruleId = ruleId;
        this.chainId = chainId;
        this.data = data;
        this.sourceIp = sourceIp;
        this.manufacturer = manufacturer;
        this.boardIp = boardIp;
        this.boardPort = boardPort;
    }

    public String getRuleId() {
        return ruleId;
    }

    public Long getChainId() {
        return chainId;
    }

    public byte[] getData() {
        return data;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getBoardIp() {
        return boardIp;
    }

    public Integer getBoardPort() {
        return boardPort;
    }
}
