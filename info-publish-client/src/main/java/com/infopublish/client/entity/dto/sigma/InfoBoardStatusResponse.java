package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

@Data
public class InfoBoardStatusResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ip;

    private boolean online;

    public static InfoBoardStatusResponse of(String ip, boolean online) {
        InfoBoardStatusResponse response = new InfoBoardStatusResponse();
        response.setIp(ip);
        response.setOnline(online);
        return response;
    }
}
