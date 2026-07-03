package com.publishgateway.udpproxy.ack;

import com.publishgateway.udpproxy.config.AckProxyProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Generates simulated JetFileII ACK packets for compare-only mode.
 */
@Component
public class AckProxySimulator {

    private static final int STATUS_OK_LOW = 0x00;
    private static final int STATUS_OK_HIGH = 0x90;

    @Resource
    private AckProxyProperties properties;

    public AckSimulationResult simulateAndCompare(byte[] request, byte[] realResponse) {
        byte[] simulated = generate(request);
        if (simulated == null) {
            return AckSimulationResult.unsupported("SIMULATION_UNSUPPORTED");
        }
        AckSimulationResult result = new AckSimulationResult();
        result.setCompared(true);
        result.setSimulatedAck(simulated);
        if (equals(simulated, realResponse)) {
            result.setMatched(true);
            result.setReason("MATCHED");
            return result;
        }
        result.setMatched(false);
        result.setReason("MISMATCHED");
        applyFirstDiff(result, simulated, realResponse);
        return result;
    }

    public byte[] generate(byte[] request) {
        if (!isSupportedRequest(request)) {
            return null;
        }
        int syn2 = request[1] & 0xFF;
        int responseSyn2;
        if (syn2 == 0xA7) {
            responseSyn2 = 0xA8;
        } else if (syn2 == 0xA3) {
            responseSyn2 = 0xA4;
        } else {
            return null;
        }

        byte[] ack = new byte[18];
        ack[0] = 0x55;
        ack[1] = (byte) responseSyn2;
        ack[4] = 0x02;
        ack[5] = 0x00;
        ack[6] = request[6];
        ack[7] = request[7];
        ack[8] = request[8];
        ack[9] = request[9];
        ack[10] = request[10];
        ack[11] = request[11];
        ack[12] = request[12];
        ack[13] = request[13];
        ack[14] = 0x00;
        ack[15] = 0x01;
        ack[16] = (byte) STATUS_OK_LOW;
        ack[17] = (byte) STATUS_OK_HIGH;
        writeChecksum(ack);
        return ack;
    }

    private boolean isSupportedRequest(byte[] request) {
        if (request == null || request.length < 16 || request[0] != 0x55) {
            return false;
        }
        int syn2 = request[1] & 0xFF;
        if (syn2 != 0xA7 && syn2 != 0xA3) {
            return false;
        }
        String command = String.format("%02X:%02X", request[12] & 0xFF, request[13] & 0xFF);
        List<String> configured = properties.getSimulateCommands();
        if (configured == null || configured.isEmpty()) {
            return true;
        }
        for (String item : configured) {
            if (item != null && command.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private void writeChecksum(byte[] packet) {
        int syn2 = packet[1] & 0xFF;
        if (syn2 == 0xA8) {
            int sum = checksumSum(packet);
            packet[2] = (byte) (sum & 0xFF);
            packet[3] = (byte) ((sum >>> 8) & 0xFF);
            return;
        }
        if (syn2 == 0xA4) {
            int crc = crc16X25(packet);
            packet[2] = (byte) ((crc >>> 8) & 0xFF);
            packet[3] = (byte) (crc & 0xFF);
        }
    }

    private int checksumSum(byte[] packet) {
        int sum = 0;
        for (int i = 4; i < packet.length; i++) {
            sum = (sum + (packet[i] & 0xFF)) & 0xFFFF;
        }
        return sum;
    }

    private int crc16X25(byte[] packet) {
        int crc = 0xFFFF;
        for (int i = 4; i < packet.length; i++) {
            crc ^= packet[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0x8408;
                } else {
                    crc >>>= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return (~crc) & 0xFFFF;
    }

    private boolean equals(byte[] left, byte[] right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        return true;
    }

    private void applyFirstDiff(AckSimulationResult result, byte[] simulated, byte[] real) {
        int simulatedLength = simulated == null ? 0 : simulated.length;
        int realLength = real == null ? 0 : real.length;
        int max = Math.max(simulatedLength, realLength);
        for (int i = 0; i < max; i++) {
            Integer simulatedByte = i < simulatedLength ? simulated[i] & 0xFF : null;
            Integer realByte = i < realLength ? real[i] & 0xFF : null;
            if (simulatedByte == null || realByte == null || !simulatedByte.equals(realByte)) {
                result.setFirstDiffOffset(i);
                result.setSimulatedByteAtDiff(simulatedByte == null ? null : String.format("%02X", simulatedByte));
                result.setRealByteAtDiff(realByte == null ? null : String.format("%02X", realByte));
                return;
            }
        }
    }
}
