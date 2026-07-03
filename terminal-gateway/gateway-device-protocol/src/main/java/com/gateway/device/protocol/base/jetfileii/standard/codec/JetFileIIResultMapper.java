package com.gateway.device.protocol.base.jetfileii.standard.codec;

import com.gateway.device.protocol.api.ResultMapper;
import com.gateway.device.protocol.base.jetfileii.standard.command.StatusCode;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;

import java.util.HashMap;
import java.util.Map;

/**
 * JetFileII 响应 → CommandResult 映射。
 *
 * <p>将 PacketMessage 中的状态码映射为标准错误码。</p>
 */
public class JetFileIIResultMapper implements ResultMapper<PacketMessage> {

    private static final Map<Short, String> STATUS_MAP = new HashMap<>();

    static {
        STATUS_MAP.put(StatusCode.OK, StandardErrorCode.SUCCESS);
        STATUS_MAP.put(StatusCode.ERR_CHECKSUM, StandardErrorCode.PROTOCOL_ERROR);
        STATUS_MAP.put(StatusCode.ERR_ADDRESS, StandardErrorCode.PROTOCOL_ERROR);
        STATUS_MAP.put(StatusCode.ERR_MAIN_CMD, StandardErrorCode.UNSUPPORTED_CAPABILITY);
        STATUS_MAP.put(StatusCode.ERR_SUB_CMD, StandardErrorCode.UNSUPPORTED_CAPABILITY);
        STATUS_MAP.put(StatusCode.ERR_PACK_LEN, StandardErrorCode.PROTOCOL_ERROR);
        STATUS_MAP.put(StatusCode.ERR_FILE_NOT_EXIST, StandardErrorCode.INVALID_PARAM);
        STATUS_MAP.put(StatusCode.ERR_FILE_EOF, StandardErrorCode.PROTOCOL_ERROR);
        STATUS_MAP.put(StatusCode.ERR_FILE_OPEN, StandardErrorCode.SYSTEM_ERROR);
        STATUS_MAP.put(StatusCode.ERR_NOT_SUPPORTED, StandardErrorCode.UNSUPPORTED_CAPABILITY);
        STATUS_MAP.put(StatusCode.ERR_DISK_FULL, StandardErrorCode.SYSTEM_ERROR);
        STATUS_MAP.put(StatusCode.ERR_DELETE_FAIL, StandardErrorCode.SYSTEM_ERROR);
        STATUS_MAP.put(StatusCode.ERR_FILE_NOT_FOUND, StandardErrorCode.INVALID_PARAM);
        STATUS_MAP.put(StatusCode.ERR_WRONG_PWD, StandardErrorCode.INVALID_PARAM);
        STATUS_MAP.put(StatusCode.ERR_TIME_SET, StandardErrorCode.SYSTEM_ERROR);
    }

    @Override
    public CommandResult map(PacketMessage response) {
        if (response == null) {
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR, "响应为空");
        }

        if (!response.isStatusReply()) {
            // 数据回送，提取 data 段内容
            byte[] data = response.getData();
            return CommandResult.success(data);
        }

        short statusCode = response.getStatusCode();
        String stdCode = STATUS_MAP.getOrDefault(statusCode, StandardErrorCode.SYSTEM_ERROR);

        if (StatusCode.isOk(statusCode)) {
            return CommandResult.success();
        }

        String message = String.format("JetFileII 错误: 0x%04X", statusCode & 0xFFFF);
        return CommandResult.failure(stdCode, message);
    }
}
