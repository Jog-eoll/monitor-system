package com.monitorplatform.common.log;

import com.alibaba.fastjson2.JSON;
import com.monitorplatform.common.entity.SysUserLogVO;
import com.monitorplatform.common.enums.OperateTypeEnum;
import com.monitorplatform.common.service.ISysUserLogService;
import com.monitorplatform.common.util.IPUtil;
import com.monitorplatform.common.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class OperateLogWriter {

    @Resource
    private ObjectProvider<ISysUserLogService> sysUserLogServiceProvider;

    public void writeClientSign(String action, String actorName, String actObj,
                                String clientIp, boolean success, Map<String, Object> detailMap) {
        write(OperateTypeEnum.SIGN.getType(), "客户端安全发布", action,
                firstNonBlank(actorName, SecurityUtils.getUsername()),
                actObj, clientIp, success, detailMap);
    }

    public void writeClientVerify(String actorName, String actObj,
                                  String clientIp, boolean success, Map<String, Object> detailMap) {
        write(OperateTypeEnum.VERIFY.getType(), "客户端安全发布", "客户端验签发布包",
                firstNonBlank(actorName, SecurityUtils.getUsername()),
                actObj, clientIp, success, detailMap);
    }

    public void writePublishContent(String gatewayId, String sourceIp, String boardIp,
                                    Integer boardPort, String contentId, String fileName,
                                    Long chainId, boolean success, Map<String, Object> extraDetail) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contentId", nullToEmpty(contentId));
        detail.put("fileName", nullToEmpty(fileName));
        detail.put("sourceIp", nullToEmpty(sourceIp));
        detail.put("gatewayId", nullToEmpty(gatewayId));
        detail.put("chainId", chainId);
        String boardTarget = boardIp;
        if (StringUtils.hasText(boardIp) && boardPort != null) {
            boardTarget = boardIp + ":" + boardPort;
            detail.put("boardTarget", boardTarget);
        } else if (StringUtils.hasText(boardIp)) {
            detail.put("boardTarget", boardIp);
        }
        if (extraDetail != null && !extraDetail.isEmpty()) {
            detail.putAll(extraDetail);
        }
        write(OperateTypeEnum.PUBLISH.getType(), "发布内容监看", "加密网关上报发布内容",
                firstNonBlank(SecurityUtils.getUsername(), gatewayId, sourceIp),
                boardTarget, sourceIp, success, detail);
    }

    private void write(String actType, String module, String action, String actorName,
                       String actObj, String clientIp, boolean success, Map<String, Object> detailMap) {
        write(actType, module, action, actorName, actObj, clientIp, success,
                detailMap != null && !detailMap.isEmpty() ? JSON.toJSONString(detailMap) : "");
    }

    public void write(String actType, String module, String action, String actorName,
                      String actObj, String clientIp, boolean success, String message) {
        SysUserLogVO vo = new SysUserLogVO();
        vo.setType("operation");
        vo.setActType(actType);
        vo.setActModule(module);
        vo.setActAction(action);
        vo.setUserId(SecurityUtils.getUserId());
        vo.setActorName(actorName);
        vo.setActObj(actObj);
        vo.setActResult(success ? "200" : "400");
        vo.setActMessage(message);

        HttpServletRequest request = SecurityUtils.getRequest();
        if (StringUtils.hasText(clientIp)) {
            vo.setClientIp(clientIp);
        } else if (request != null) {
            vo.setClientIp(IPUtil.getRealRequestIp(request));
        }

        if (!StringUtils.hasText(vo.getActorName())) {
            vo.setActorName(SecurityUtils.getUkeyIdentity());
        }

        ISysUserLogService service = sysUserLogServiceProvider.getIfAvailable();
        if (service == null) {
            log.warn("[operate-log] ISysUserLogService not found, skip. action={}", action);
            return;
        }
        try {
            service.insert(vo);
        } catch (Exception e) {
            log.warn("[operate-log] insert failed, action={}, error={}", action, e.getMessage());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}