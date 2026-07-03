package com.gateway.auth.ukey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UKey生命周期状态机
 *
 * 状态流转：
 * IDLE --[UKey插入]--> UKEY_DETECTED
 * UKEY_DETECTED --[读取证书/校验]--> UKEY_VALIDATED / ERROR
 * UKEY_VALIDATED --[发起认证]--> AUTHENTICATING
 * AUTHENTICATING --[认证成功]--> AUTHENTICATED
 * AUTHENTICATED --[通道建立]--> CHANNEL_ACTIVE
 * 任何状态 --[UKey拔出]--> IDLE（触发断开/清理）
 */
@Slf4j
@Component
public class UkeyLifecycleManager {

    /**
     * UKey生命周期状态枚举
     */
    public enum State {
        IDLE("空闲，等待UKey插入"),
        UKEY_DETECTED("UKey已检测到，读取证书中"),
        UKEY_VALIDATED("UKey本地校验通过"),
        AUTHENTICATING("正在与管控平台双向认证"),
        AUTHENTICATED("认证成功，可进行业务"),
        CHANNEL_ACTIVE("转发通道已建立"),
        ERROR("异常状态");

        private final String description;

        State(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 状态变更监听器
     */
    public interface StateChangeListener {
        void onStateChanged(State oldState, State newState, String message);
    }

    /** 当前状态 */
    private volatile State currentState = State.IDLE;

    /** 上一次错误信息 */
    private volatile String lastError = null;

    /** 当前UKey的证书编号 */
    private volatile String currentCertSerialNo = null;

    /** 当前UKey的路径 */
    private volatile String currentUkeyPath = null;

    /** 状态变更监听器列表 */
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 注册状态变更监听器
     */
    public void addStateChangeListener(StateChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * 获取当前状态
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * 获取上一次错误信息
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * 获取当前证书编号
     */
    public String getCurrentCertSerialNo() {
        return currentCertSerialNo;
    }

    /**
     * 获取当前UKey路径
     */
    public String getCurrentUkeyPath() {
        return currentUkeyPath;
    }

    /**
     * UKey检测到 -> UKEY_DETECTED
     */
    public synchronized boolean onUkeyDetected(String ukeyPath, String certSerialNo) {
        if (currentState != State.IDLE && currentState != State.ERROR) {
            log.warn("无法进入UKEY_DETECTED状态，当前状态: {}", currentState);
            return false;
        }

        this.currentUkeyPath = ukeyPath;
        this.currentCertSerialNo = certSerialNo;
        this.lastError = null;
        transition(State.UKEY_DETECTED, "检测到UKey: " + certSerialNo);
        return true;
    }

    /**
     * UKey证书校验通过 -> UKEY_VALIDATED
     */
    public synchronized boolean onUkeyValidated() {
        if (currentState != State.UKEY_DETECTED) {
            log.warn("无法进入UKEY_VALIDATED状态，当前状态: {}", currentState);
            return false;
        }

        transition(State.UKEY_VALIDATED, "证书校验通过: " + currentCertSerialNo);
        return true;
    }

    /**
     * 开始认证 -> AUTHENTICATING
     */
    public synchronized boolean onAuthenticating() {
        if (currentState != State.UKEY_VALIDATED) {
            log.warn("无法进入AUTHENTICATING状态，当前状态: {}", currentState);
            return false;
        }

        transition(State.AUTHENTICATING, "正在进行双向认证...");
        return true;
    }

    /**
     * 认证成功 -> AUTHENTICATED
     */
    public synchronized boolean onAuthenticated() {
        if (currentState != State.AUTHENTICATING) {
            log.warn("无法进入AUTHENTICATED状态，当前状态: {}", currentState);
            return false;
        }

        transition(State.AUTHENTICATED, "双向认证成功");
        return true;
    }

    /**
     * 通道建立 -> CHANNEL_ACTIVE
     */
    public synchronized boolean onChannelActive() {
        if (currentState != State.AUTHENTICATED) {
            log.warn("无法进入CHANNEL_ACTIVE状态，当前状态: {}", currentState);
            return false;
        }

        transition(State.CHANNEL_ACTIVE, "转发通道已建立");
        return true;
    }

    /**
     * 发生错误 -> ERROR
     */
    public synchronized void onError(String errorMessage) {
        this.lastError = errorMessage;
        transition(State.ERROR, "错误: " + errorMessage);
    }

    /**
     * UKey拔出 / 主动退出 -> IDLE
     * 从任何状态都可以回到IDLE
     */
    public synchronized void reset(String reason) {
        this.currentCertSerialNo = null;
        this.currentUkeyPath = null;
        this.lastError = null;
        transition(State.IDLE, reason != null ? reason : "状态已重置");
    }

    /**
     * 判断是否已认证（AUTHENTICATED 或 CHANNEL_ACTIVE）
     */
    public boolean isAuthenticated() {
        return currentState == State.AUTHENTICATED || currentState == State.CHANNEL_ACTIVE;
    }

    /**
     * 判断通道是否活跃
     */
    public boolean isChannelActive() {
        return currentState == State.CHANNEL_ACTIVE;
    }

    /**
     * 判断UKey是否已插入（非IDLE和ERROR状态）
     */
    public boolean isUkeyPresent() {
        return currentState != State.IDLE && currentState != State.ERROR;
    }

    /**
     * 获取状态概要信息
     */
    public StatusOverview getStatusOverview() {
        return new StatusOverview(
                currentState,
                currentState.getDescription(),
                currentCertSerialNo,
                currentUkeyPath,
                lastError,
                isAuthenticated(),
                isChannelActive()
        );
    }

    /**
     * 执行状态转换
     */
    private void transition(State newState, String message) {
        State oldState = this.currentState;
        this.currentState = newState;

        log.info("状态转换: {} -> {} ({})", oldState, newState, message);

        // 通知监听器
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(oldState, newState, message);
            } catch (Exception e) {
                log.error("通知状态变更监听器失败", e);
            }
        }
    }

    /**
     * 状态概要
     */
    public static class StatusOverview {
        public final State state;
        public final String stateDescription;
        public final String certSerialNo;
        public final String ukeyPath;
        public final String lastError;
        public final boolean authenticated;
        public final boolean channelActive;

        public StatusOverview(State state, String stateDescription, String certSerialNo,
                              String ukeyPath, String lastError, boolean authenticated, boolean channelActive) {
            this.state = state;
            this.stateDescription = stateDescription;
            this.certSerialNo = certSerialNo;
            this.ukeyPath = ukeyPath;
            this.lastError = lastError;
            this.authenticated = authenticated;
            this.channelActive = channelActive;
        }
    }
}
