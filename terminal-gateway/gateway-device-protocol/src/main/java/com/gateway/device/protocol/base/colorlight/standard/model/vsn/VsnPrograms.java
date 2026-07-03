package com.gateway.device.protocol.base.colorlight.standard.model.vsn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VsnPrograms {
    private Programs programs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Programs {
        private VsnProgram program;
    }
}
