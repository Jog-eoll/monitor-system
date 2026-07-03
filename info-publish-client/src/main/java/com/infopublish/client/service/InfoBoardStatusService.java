package com.infopublish.client.service;

import com.infopublish.client.entity.dto.sigma.InfoBoardStatusResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;

public interface InfoBoardStatusService {

    InfoBoardStatusResponse getStatus(String ip, Integer port);

    BoardEndpoint resolveEndpoint(SigmaVerifyRequest.TargetRef target);

    class BoardEndpoint {
        private final String ip;
        private final Integer port;
        private final boolean enabled;

        public BoardEndpoint(String ip, Integer port, boolean enabled) {
            this.ip = ip;
            this.port = port;
            this.enabled = enabled;
        }

        public String getIp() {
            return ip;
        }

        public Integer getPort() {
            return port;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
