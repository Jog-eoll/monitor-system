package com.monitorplatform.common.util;

import io.micrometer.core.instrument.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2023/4/27 15:50:41
 */
public class IPUtil {

    /**
     * 获取Ip地址(真是请求IP)
     * @param request
     * @return
     */
    public static String getRealRequestIp(HttpServletRequest request) {
        String Xip = request.getHeader("X-Real-IP");
        String XFor = request.getHeader("X-Forwarded-For");
        if(StringUtils.isNotEmpty(XFor) && !"unKnown".equalsIgnoreCase(XFor)){
            //多次反向代理后会有多个ip值，第一个ip才是真实ip
            int index = XFor.indexOf(",");
            if(index != -1){
                return XFor.substring(0,index);
            }else{
                return XFor;
            }
        }
        XFor = Xip;
        if(StringUtils.isNotEmpty(XFor) && !"unKnown".equalsIgnoreCase(XFor)){
            return XFor;
        }
        if (StringUtils.isBlank(XFor) || "unknown".equalsIgnoreCase(XFor)) {
            XFor = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(XFor) || "unknown".equalsIgnoreCase(XFor)) {
            XFor = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(XFor) || "unknown".equalsIgnoreCase(XFor)) {
            XFor = request.getHeader("HTTP_CLIENT_IP");
        }
        if (StringUtils.isBlank(XFor) || "unknown".equalsIgnoreCase(XFor)) {
            XFor = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (StringUtils.isBlank(XFor) || "unknown".equalsIgnoreCase(XFor)) {
            XFor = request.getRemoteAddr();
        }
        return XFor;
    }


    public static boolean isUrlValid(String strLink) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(strLink);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);
            String strMessage = conn.getResponseMessage();
            if (strMessage.compareTo("Not Found") == 0) {
                return false;
            }
            conn.disconnect();
        } catch (Exception e) {
            return false;
        }finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return true;
    }


}
