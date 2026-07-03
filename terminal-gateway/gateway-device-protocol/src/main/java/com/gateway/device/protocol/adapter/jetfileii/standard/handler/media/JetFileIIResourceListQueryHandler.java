package com.gateway.device.protocol.adapter.jetfileii.standard.handler.media;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.MainCmd;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.base.jetfileii.standard.command.ProtocolConst;
import com.gateway.device.protocol.base.jetfileii.standard.command.SubCmd;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ListQueryParams;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资源列表查询 — 按资源类型设置默认过滤器列表，大小写不敏感。
 *
 * <p>每个 DeviceCapability (IMAGE_LIST_QUERY / VIDEO_LIST_QUERY / ...) 构造一个实例。
 * 过滤器在发送前自动拆分为大小写双版本，确保大小写敏感的设备文件系统也能完整匹配。</p>
 */
public class JetFileIIResourceListQueryHandler extends AbstractJetFileIIHandler<ListQueryParams> {

    private final DeviceCapability<ListQueryParams> capability;
    private final List<String> defaultFilters;

    public JetFileIIResourceListQueryHandler(JetFileIIMessaging messaging, DeviceTransport transport,
                                             DeviceCapability<ListQueryParams> capability,
                                             List<String> defaultFilters) {
        super(messaging, transport);
        this.capability = capability;
        this.defaultFilters = defaultFilters != null
                ? Collections.unmodifiableList(new ArrayList<>(defaultFilters))
                : Collections.emptyList();
    }

    @Override
    public DeviceCapability<ListQueryParams> capability() {
        return capability;
    }

    @Override
    public CommandResult execute(DeviceContext device, ListQueryParams params) {
        List<String> filters = resolveFilters(params);
        if (filters.size() <= 1) {
            JetFileIIRequest req = buildRequest(device, params);
            return messaging().executeSimple(transport(), device, req, getTimeout());
        }
        List<byte[]> allData = new ArrayList<>();
        for (String filter : filters) {
            ListQueryParams singleParams = ListQueryParams.builder()
                    .filter(filter).path(params.getPath()).partition(params.getPartition()).build();
            JetFileIIRequest req = buildRequest(device, singleParams);
            CommandResult result = messaging().executeSimple(transport(), device, req, getTimeout());
            if (result.isSuccess() && result.getData() != null) {
                allData.add((byte[]) result.getData());
            }
        }
        int totalLen = allData.stream().mapToInt(b -> b.length).sum();
        byte[] merged = new byte[totalLen];
        int pos = 0;
        for (byte[] chunk : allData) {
            System.arraycopy(chunk, 0, merged, pos, chunk.length);
            pos += chunk.length;
        }
        return CommandResult.success(merged);
    }

    private JetFileIIRequest buildRequest(DeviceContext device, ListQueryParams params) {
        String directPath = params.getPath();
        String path;
        if (StringUtils.isNotBlank(directPath)) {
            path = directPath;
        } else {
            Partition partition = params.getPartition();
            String filter = resolveSingleFilter(params);
            path = partition.getDrive() + ":\\" + filter;
        }

        byte[] pathBytes = (path + "\0").getBytes(ProtocolConstant.GB18030);
        int argLen = (pathBytes.length + ProtocolConst.ARG_UNIT_SIZE - 1)
                / ProtocolConst.ARG_UNIT_SIZE;
        byte[] arg = new byte[argLen * ProtocolConst.ARG_UNIT_SIZE];
        System.arraycopy(pathBytes, 0, arg, 0, pathBytes.length);

        int gg = JetFileIIMessaging.resolveGg(device);
        int uu = JetFileIIMessaging.resolveUu(device);
        return JetFileIIRequest.builder()
                .mainCmd(MainCmd.FILE_CTL)
                .subCmd(SubCmd.FILE_DIR)
                .destGg(gg).destUu(uu)
                .arg(arg).needReply(true).build();
    }

    private List<String> resolveFilters(ListQueryParams p) {
        Object val = p.getFilter();
        if (val instanceof List<?>) {
            List<String> raw = ((List<?>) val).stream()
                    .map(Object::toString).collect(Collectors.toList());
            if (!raw.isEmpty()) return raw;
        }
        if (val instanceof String && !((String) val).isEmpty()) {
            return Collections.singletonList((String) val);
        }
        return defaultFilters;
    }

    private String resolveSingleFilter(ListQueryParams p) {
        return resolveFilters(p).get(0);
    }
}
