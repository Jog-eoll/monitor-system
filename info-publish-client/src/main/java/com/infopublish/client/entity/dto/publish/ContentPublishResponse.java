package com.infopublish.client.entity.dto.publish;

import com.infopublish.client.entity.dto.precheck.BasicChecks;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Stable response for content publish execution.
 */
@Data
public class ContentPublishResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;

    private String code;

    private String message;

    private String requestId;

    private PlaylistSummary playlist;

    private SigmaVerifyRequest.TargetRef target;

    private CheckSummary checks;

    private DeliverySummary delivery;

    public static ContentPublishResponse rejected(String requestId, String code, String message) {
        ContentPublishResponse response = new ContentPublishResponse();
        response.success = false;
        response.code = code;
        response.message = message;
        response.requestId = requestId;
        return response;
    }

    public static ContentPublishResponse error(String requestId, String code, String message) {
        ContentPublishResponse response = new ContentPublishResponse();
        response.success = false;
        response.code = code;
        response.message = message;
        response.requestId = requestId;
        return response;
    }

    public static ContentPublishResponse accepted(String requestId,
                                                  PrecheckResponse precheck,
                                                  String deliveryTaskId,
                                                  Map<String, Object> deliveryRaw) {
        ContentPublishResponse response = new ContentPublishResponse();
        response.success = true;
        response.code = "ACCEPTED";
        response.message = "发布任务已提交";
        response.requestId = requestId;
        if (precheck != null) {
            response.playlist = PlaylistSummary.of(precheck.getPlaylistId(),
                    precheck.getPlaylistDigest(), precheck.getItems());
            response.target = precheck.getTarget();
            response.checks = CheckSummary.of(precheck, true);
        }
        response.delivery = DeliverySummary.of(deliveryTaskId, deliveryRaw);
        return response;
    }

    public void attachProgram(PrecheckResponse precheck,
                              SigmaVerifyRequest.TargetRef target,
                              String playlistId,
                              String playlistDigest,
                              List<SigmaVerifyRequest.PlaylistItem> items,
                              boolean filesVerified) {
        this.playlist = PlaylistSummary.of(firstNonBlank(
                        precheck != null ? precheck.getPlaylistId() : null, playlistId),
                firstNonBlank(precheck != null ? precheck.getPlaylistDigest() : null, playlistDigest),
                items);
        this.target = target;
        this.checks = CheckSummary.of(precheck, filesVerified);
    }

    public void attachDelivery(String deliveryTaskId, Map<String, Object> deliveryRaw) {
        this.delivery = DeliverySummary.of(deliveryTaskId, deliveryRaw);
    }

    private static String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Data
    public static class PlaylistSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private String playlistId;
        private String digest;
        private Integer itemCount;
        private List<SigmaVerifyRequest.PlaylistItem> items;

        public static PlaylistSummary of(String playlistId,
                                         String digest,
                                         List<SigmaVerifyRequest.PlaylistItem> items) {
            PlaylistSummary summary = new PlaylistSummary();
            summary.playlistId = playlistId;
            summary.digest = digest;
            summary.itemCount = items != null ? items.size() : 0;
            summary.items = items;
            return summary;
        }
    }

    @Data
    public static class CheckSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean passed;
        private boolean ukeyAuthenticated;
        private boolean clientAuthenticated;
        private boolean sigmaProcessBound;
        private boolean gatewayReady;
        private boolean filesVerified;

        public static CheckSummary of(PrecheckResponse precheck, boolean filesVerified) {
            CheckSummary summary = new CheckSummary();
            BasicChecks basicChecks = precheck != null ? precheck.getChecks() : null;
            if (basicChecks != null) {
                summary.ukeyAuthenticated = basicChecks.isUkeyAuthenticated();
                summary.clientAuthenticated = basicChecks.isClientAuthenticated();
                summary.sigmaProcessBound = basicChecks.isSigmaProcessBound();
                summary.gatewayReady = basicChecks.isGatewayReady();
            }
            summary.filesVerified = filesVerified;
            summary.passed = precheck != null && precheck.isPublishAllowed() && filesVerified;
            return summary;
        }
    }

    @Data
    public static class DeliverySummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private String deliveryTaskId;
        private String status;
        private Long createdAt;

        public static DeliverySummary of(String deliveryTaskId, Map<String, Object> deliveryRaw) {
            DeliverySummary summary = new DeliverySummary();
            summary.deliveryTaskId = deliveryTaskId;
            if (deliveryRaw != null) {
                Object status = deliveryRaw.get("status");
                Object createdAt = deliveryRaw.get("createdAt");
                summary.status = status != null ? String.valueOf(status) : null;
                summary.createdAt = parseLong(createdAt);
            }
            return summary;
        }

        private static Long parseLong(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (Exception e) {
                return null;
            }
        }
    }
}
