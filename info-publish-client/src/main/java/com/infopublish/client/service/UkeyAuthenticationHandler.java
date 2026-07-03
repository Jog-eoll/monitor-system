package com.infopublish.client.service;

import java.util.Map;

public interface UkeyAuthenticationHandler {

    boolean isProcessSelectionPending();

    Map<String, Object> completePendingProcessSelection();

    void logout();
}
