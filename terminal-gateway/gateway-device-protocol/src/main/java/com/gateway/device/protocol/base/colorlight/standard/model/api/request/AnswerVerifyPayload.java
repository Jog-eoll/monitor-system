package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/verify_answer 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerVerifyPayload {

    private String question;
    private String answer;
}
