package com.vw.isds.register.service;


import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/2/2 11:41:30
 */
public class LinuxServerInfos extends AbstractServerInfos {

    @Override
    protected List<String> getMacAddress() throws Exception {
        return getMac();
    }

    @Override
    protected String getCPUSerial() throws Exception {
        //序列号
        String serialNumber = "";

        //使用dmidecode命令获取CPU序列号
        String[] shell = {"/bin/bash", "-c", "dmidecode -t processor | grep 'ID' | awk -F ':' '{print $2}' | head -n 1"};
        Process process = Runtime.getRuntime().exec(shell);
        process.getOutputStream().close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = reader.readLine().trim();
        if (StringUtils.isNotBlank(line)) {
            serialNumber = line;
        }
        reader.close();
        return serialNumber;

    }

    @Override
    protected String getMainBoardSerial() throws Exception {
        //序列号
        String serialNumber = "";

        //使用dmidecode命令获取主板序列号
        String[] shell = {"/bin/bash", "-c", "dmidecode | grep 'Serial Number' | awk -F ':' '{print $2}' | head -n 1"};
        Process process = Runtime.getRuntime().exec(shell);
        process.getOutputStream().close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = reader.readLine().trim();
        if (StringUtils.isNotBlank(line)) {
            serialNumber = line;
        }

        reader.close();
        return serialNumber;
    }
}
