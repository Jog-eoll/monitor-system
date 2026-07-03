package com.infopublish.client.service;

import com.infopublish.client.entity.dto.precheck.FailedFile;
import com.infopublish.client.entity.dto.precheck.PassedFile;
import com.infopublish.client.entity.dto.sigma.InnerFile;

/**
 * 文件校验服务
 * <p>
 * 对 securityCheckRequired=true 的内部文件执行 GENERIC_FILE_CHECK_V1 校验。
 * </p>
 */
public interface FileCheckService {

    /**
     * 校验单个内部文件
     *
     * @param sigmaBaseUrl Sigma 接口根地址
     * @param playlistId   待播放列表 ID
     * @param innerFile    内部文件元信息
     * @return 校验结果：PassedFile 或 FailedFile（互斥，其中一个为 null）
     */
    FileCheckOutcome checkFile(String sigmaBaseUrl, String playlistId, InnerFile innerFile);

    /**
     * 文件校验结果（通过或失败互斥）
     */
    class FileCheckOutcome {
        private final PassedFile passedFile;
        private final FailedFile failedFile;

        private FileCheckOutcome(PassedFile passedFile, FailedFile failedFile) {
            this.passedFile = passedFile;
            this.failedFile = failedFile;
        }

        public static FileCheckOutcome pass(PassedFile file) {
            return new FileCheckOutcome(file, null);
        }

        public static FileCheckOutcome fail(FailedFile file) {
            return new FileCheckOutcome(null, file);
        }

        public boolean isPassed() {
            return passedFile != null;
        }

        public PassedFile getPassedFile() {
            return passedFile;
        }

        public FailedFile getFailedFile() {
            return failedFile;
        }
    }
}
