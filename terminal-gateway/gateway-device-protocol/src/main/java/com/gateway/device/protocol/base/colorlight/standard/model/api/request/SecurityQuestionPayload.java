package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /api/question 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionPayload {

    private Boolean enable;
    private String password;
    private List<Question> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Question {
        private String question;
        private String answer;
    }
}
