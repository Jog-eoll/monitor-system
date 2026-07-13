package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;

import java.util.Locale;

/**
 * Normalizes QingSong playlist fields that are optional in the upstream callback
 * but required by the local publish permit digest and delivery contract.
 */
final class QingsongPlaylistDurationNormalizer {

    static final int DEFAULT_VIDEO_DURATION_SECONDS = 30;

    private QingsongPlaylistDurationNormalizer() {
    }

    static void normalizeVideoDuration(QingsongProgramResponse.ProgramData program) {
        if (program == null || program.getItems() == null) {
            return;
        }
        for (SigmaVerifyRequest.PlaylistItem item : program.getItems()) {
            if (item == null) {
                continue;
            }
            if (isVideo(item) && !isPositive(item.getDurationSeconds())) {
                item.setDurationSeconds(DEFAULT_VIDEO_DURATION_SECONDS);
            }
        }
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private static boolean isVideo(SigmaVerifyRequest.PlaylistItem item) {
        String fileType = normalize(item.getFileType());
        if ("VIDEO".equals(fileType) || "MP4".equals(fileType)
                || "FLV".equals(fileType) || "AVI".equals(fileType)) {
            return true;
        }
        String fileName = normalize(item.getFileName());
        return fileName.endsWith(".MP4")
                || fileName.endsWith(".FLV")
                || fileName.endsWith(".AVI");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
