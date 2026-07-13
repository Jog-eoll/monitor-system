package com.publishgateway.udpproxy.service;

public interface ContentReportStrategy<T> {

    ContentReportMode mode();

    void report(T request);
}
