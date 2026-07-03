package com.gateway.device.protocol.base.jetfileii.standard.codec;

import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.jetfileii.standard.JetFileIIParser;
import com.gateway.device.protocol.base.jetfileii.standard.PacketBuilder;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;

/**
 * JetFileII 协议编解码器 —— JetFileIIRequest ↔ byte[]。
 */
public class JetFileIICodec implements ProtocolCodec<JetFileIIRequest, PacketMessage> {

    @Override
    public byte[] encode(JetFileIIRequest request) {
        PacketBuilder builder = PacketBuilder.create(request.getMainCmd(), request.getSubCmd());
        if (request.isBroadcast()) {
            builder.broadcast();
        } else {
            builder.destAddr(request.getDestGg(), request.getDestUu());
        }
        if (request.isNeedReply()) {
            builder.needReply();
        } else {
            builder.noReply();
        }
        if (request.isUseCrc()) {
            builder.withCrc();
        }
        if (request.getArg() != null) {
            builder.arg(request.getArg());
        }
        if (request.getData() != null) {
            builder.data(request.getData());
        }
        return builder.buildBytes();
    }

    @Override
    public PacketMessage decode(byte[] response) {
        return JetFileIIParser.parse(response);
    }
}
