package com.infopublish.client.service;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Set;

/**
 * 进程绑定服务接口
 *
 * <p>负责将 UKey 认证会话与指定大屏控制软件进程绑定。
 * UKey 认证成功后，先在受控目录内按 target-process-name 发现可执行文件，
 * 再按 target-process-path 与 target-process-md5 校验，最后拉起或绑定 PID。
 * UKey 拔出时同步清除白名单。
 *
 * <p>严格策略：目标进程未完成绑定时，来源校验返回 false（拒绝）。
 */
public interface ProcessBindService {

    /**
     * 通过配置的 target-process-path 查找目标进程 PID
     *
     * <p>不再按 target-process-name / Sigma Play.exe 扫描进程候选 PID；
     * target-process-name 只作为受控目录内的文件发现入口。
     *
     * @return 找到时返回进程 PID（大于 0）；未找到返回 -1L
     */
    long resolveTargetPid();

    /**
     * 扫描受控目录内的目标进程候选文件。
     *
     * <p>该方法只做文件发现与校验状态计算，不启动进程，不修改当前 PID 绑定。
     */
    ProcessCandidatesResult scanProcessCandidates();

    /**
     * 打开本机文件选择器，将用户选择的 Sigma Play.exe 加入候选列表。
     */
    ProcessSelectionResult requestProcessCandidatePicker();

    /**
     * 鎺ユ敹 watcher/helper 鍦ㄤ氦浜掓闈㈤€夋嫨鍒扮殑 Sigma Play.exe 璺緞銆?
     */
    ProcessSelectionResult addProcessCandidateFromHelper(String selectedPath);

    /**
     * 根据候选 ID 启动并绑定进程。
     *
     * <p>后端根据候选 ID 查找真实路径并重新校验，不信任前端传回的候选状态。
     */
    ProcessSelectionResult selectAndStartCandidate(String candidateId);

    /**
     * 清空候选缓存。
     */
    void clearProcessCandidateCache();

    /**
     * 在 UKey 认证成功后绑定目标进程 PID
     *
     * <p>auto-start 启用且未找到目标进程时，按 target-process-path 拉起后再绑定 PID。
     * PID 绑定成功后自动调用 resolveAuthorizedEndpoints() 刷新本机 IP 白名单。
     * 若目标进程未运行或校验失败，authorizedPid = -1，后续来源校验拒绝。
     */
    void bindCurrentPid();

    /**
     * 通过当前授权 PID 刷新本机合法来源 IP 白名单。
     *
     * <p>Sigma Play 使用短生命周期 UDP 端口，端口可能在采样前关闭；
     * 因此当前白名单以本机 IPv4 地址为主，实时来源端口优先通过 WinDivert 校验 PID 归属，
     * 未启用或未命中时回退到 GetExtendedUdpTable。
     */
    void resolveAuthorizedEndpoints();

    /**
     * 校验来源 IP:Port 是否在白名单中
     *
     * @param ip   来源 IP 字符串
     * @param port 来源端口号
     * @return 合法返回 true；进程未绑定或 PID 校验失败时返回 false
     */
    boolean isEndpointAuthorized(String ip, int port);

    /**
     * 复核当前授权 PID 是否仍对应合法目标程序。
     *
     * @param trigger 触发来源，如 validate-source、scheduled
     * @return 完整性复核结果
     */
    ProcessIntegrityResult verifyAuthorizedProcessIntegrity(String trigger);

    /**
     * 清除进程绑定及白名单（UKey 拔出时调用）
     *
     * <p>将 authorizedPid 重置为 -1L，并清空 authorizedEndpoints。
     */
    void clearBinding();

    /**
     * 获取当前已绑定的授权 PID
     *
     * @return 已绑定的 PID；-1L 表示未绑定或目标进程未运行
     */
    long getAuthorizedPid();

    /**
     * 获取当前合法来源白名单（用于状态展示和调试）
     *
     * @return IP:Port 字符串集合，如 {"192.168.1.100:52341", "192.168.1.100:52342"}
     */
    Set<String> getAuthorizedEndpoints();

    /**
     * 刷新进程绑定（目标进程重启后主动调用）
     *
     * <p>重新执行进程查找并更新 authorizedPid 和 authorizedEndpoints。
     */
    void refreshBinding();

    class ProcessCandidatesResult {
        private final String targetProcessName;
        private final String targetProcessPath;
        private final List<String> searchRoots;
        private final List<ProcessCandidateInfo> candidates;
        private final long authorizedPid;

        public ProcessCandidatesResult(String targetProcessName,
                                       String targetProcessPath,
                                       List<String> searchRoots,
                                       List<ProcessCandidateInfo> candidates,
                                       long authorizedPid) {
            this.targetProcessName = targetProcessName;
            this.targetProcessPath = targetProcessPath;
            this.searchRoots = searchRoots;
            this.candidates = candidates;
            this.authorizedPid = authorizedPid;
        }

        public String getTargetProcessName() { return targetProcessName; }
        @JsonIgnore
        public String getTargetProcessPath() { return targetProcessPath; }
        @JsonIgnore
        public List<String> getSearchRoots() { return searchRoots; }
        public List<ProcessCandidateInfo> getCandidates() { return candidates; }
        @JsonIgnore
        public long getAuthorizedPid() { return authorizedPid; }
    }

    class ProcessCandidateInfo {
        private final String candidateId;
        private final String path;
        private final String fileName;
        private final boolean pathMatched;
        private final boolean md5Matched;
        private final boolean valid;
        private final long runningPid;
        private final String reason;

        public ProcessCandidateInfo(String candidateId,
                                    String path,
                                    String fileName,
                                    boolean pathMatched,
                                    boolean md5Matched,
                                    boolean valid,
                                    long runningPid,
                                    String reason) {
            this.candidateId = candidateId;
            this.path = path;
            this.fileName = fileName;
            this.pathMatched = pathMatched;
            this.md5Matched = md5Matched;
            this.valid = valid;
            this.runningPid = runningPid;
            this.reason = reason;
        }

        public String getCandidateId() { return candidateId; }
        @JsonIgnore
        public String getPath() { return path; }
        public String getFileName() { return fileName; }
        @JsonIgnore
        public boolean isPathMatched() { return pathMatched; }
        @JsonIgnore
        public boolean isMd5Matched() { return md5Matched; }
        public boolean isValid() { return valid; }
        @JsonIgnore
        public long getRunningPid() { return runningPid; }
        @JsonIgnore
        public String getReason() { return reason; }
    }

    class ProcessSelectionResult {
        private final boolean success;
        private final long authorizedPid;
        private final String message;
        private final ProcessCandidateInfo candidate;

        public ProcessSelectionResult(boolean success,
                                      long authorizedPid,
                                      String message,
                                      ProcessCandidateInfo candidate) {
            this.success = success;
            this.authorizedPid = authorizedPid;
            this.message = message;
            this.candidate = candidate;
        }

        public boolean isSuccess() { return success; }
        public long getAuthorizedPid() { return authorizedPid; }
        public String getMessage() { return message; }
        public ProcessCandidateInfo getCandidate() { return candidate; }
    }

    class ProcessIntegrityResult {
        private final boolean valid;
        private final boolean checked;
        private final String reasonCode;
        private final String reason;

        public ProcessIntegrityResult(boolean valid, boolean checked, String reasonCode, String reason) {
            this.valid = valid;
            this.checked = checked;
            this.reasonCode = reasonCode;
            this.reason = reason;
        }

        public boolean isValid() { return valid; }
        public boolean isChecked() { return checked; }
        public String getReasonCode() { return reasonCode; }
        public String getReason() { return reason; }
    }
}
