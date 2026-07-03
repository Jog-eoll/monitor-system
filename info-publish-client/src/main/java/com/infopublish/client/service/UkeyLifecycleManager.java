package com.infopublish.client.service;

public interface UkeyLifecycleManager {

    void addStateChangeListener(StateChangeListener listener);

    State getCurrentState();

    String getLastError();

    String getCurrentCertSerialNo();

    String getCurrentUkeyPath();

    boolean onUkeyDetected(String ukeyPath, String certSerialNo);

    boolean onUkeyValidated();

    boolean onAuthenticating();

    boolean onAuthenticated();

    boolean onProcessSelectionRequired();

    boolean onProcessBound();

    boolean onChannelActive();

    void onError(String errorMessage);

    void reset(String reason);

    boolean isAuthenticated();

    boolean isChannelActive();

    boolean isUkeyPresent();

    StatusOverview getStatusOverview();

    interface StateChangeListener {
        void onStateChanged(State oldState, State newState, String message);
    }

    enum State {
        IDLE("idle"),
        UKEY_DETECTED("ukey detected"),
        UKEY_VALIDATED("ukey validated"),
        AUTHENTICATING("authenticating"),
        AUTHENTICATED("authenticated"),
        PROCESS_SELECTION_REQUIRED("process selection required"),
        CHANNEL_ACTIVE("channel active"),
        ERROR("error");

        private final String description;

        State(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    class StatusOverview {
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
