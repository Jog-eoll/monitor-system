package com.publishgateway.udpproxy.service.impl;

import com.publishgateway.udpproxy.service.TranscodeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


@Service
@ConditionalOnProperty(name = "gateway.transcode.enabled",havingValue = "false",matchIfMissing = true)
public class PassthroughTranscodeServiceImpl implements TranscodeService {


    @Override
    public byte[] transcode(byte[] data, DataType dataType) {

        // 透传模式
        return data;
    }


}
