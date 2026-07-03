package com.monitorplatform.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.*;
import com.monitorplatform.device.handler.CommandHandler;
import com.monitorplatform.device.handler.CommandHandlerFactory;
import com.monitorplatform.device.mapper.UnifiedDeviceMapper;
import com.monitorplatform.device.service.UnifiedDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 统一设备管理服务实现类
 */
@Slf4j
@Service
public class UnifiedDeviceServiceImpl implements UnifiedDeviceService {

    @Resource
    private UnifiedDeviceMapper unifiedDeviceMapper;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RestTemplate restTemplate;


    @Resource
    private CommandHandlerFactory commandHandlerFactory;

    /**
     * 心跳超时时间（秒）
     */
    private static final int HEARTBEAT_TIMEOUT = 60;

    private static final String HEARTBEAT_KEY_PREFIX = "device:heartbeat:";
    private static final String STATUS_KEY_PREFIX = "device:status:";

    /**
     * 获取设备分类树
     */
    @Override
    public List<DeviceTreeNodeDTO> getDeviceTree() {
        List<DeviceTreeNodeDTO> tree = new ArrayList<>();

        List<String> types = Arrays.asList("publish_server", "publish_gateway", "terminal_encrypt_gateway", "content_server", "info_board", "camera");

        for (String type : types) {
            DeviceTreeNodeDTO node = new DeviceTreeNodeDTO();
            node.setId(type);
            node.setLabel(getDeviceTypeLabel(type));
            node.setType("category");
            node.setDeviceType(type);

            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceType, type);
            List<UnifiedDevice> devices = unifiedDeviceMapper.selectList(wrapper);

            node.setCount(devices.size());
            node.setOnlineCount((int) devices.stream()
                .filter(d -> "在线".equals(d.getStatus()) || "正常".equals(d.getStatus())).count());
            node.setOfflineCount(devices.size() - node.getOnlineCount());

            List<DeviceTreeNodeDTO> children = devices.stream().map(device -> {
                DeviceTreeNodeDTO child = new DeviceTreeNodeDTO();
                child.setId(device.getDeviceType() + "-" + device.getId());
                child.setLabel(device.getDeviceName() + (device.getIpAddress() != null ? " (" + device.getIpAddress() + ")" : ""));
                child.setType("device");
                child.setDeviceType(device.getDeviceType());
                return child;
            }).collect(Collectors.toList());

            node.setChildren(children);
            tree.add(node);
        }

        return tree;
    }

    /**
     * 统一分页查询所有设备
     */
    @Override
    public Page<UnifiedDeviceDTO> pageListAllDevices(Integer pageNum, Integer pageSize,
                                                      String deviceType, String status, String keyword) {
        Page<UnifiedDevice> devicePage = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(deviceType)) {
            wrapper.eq(UnifiedDevice::getDeviceType, deviceType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(UnifiedDevice::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(UnifiedDevice::getDeviceName, keyword)
                .or().like(UnifiedDevice::getDeviceId, keyword)
                .or().like(UnifiedDevice::getIpAddress, keyword)
            );
        }

        wrapper.orderByDesc(UnifiedDevice::getCreateTime);

        unifiedDeviceMapper.selectPage(devicePage, wrapper);

        Page<UnifiedDeviceDTO> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(devicePage.getTotal());

        List<UnifiedDeviceDTO> dtoList = devicePage.getRecords().stream().map(device -> {
            UnifiedDeviceDTO dto = new UnifiedDeviceDTO();
            BeanUtils.copyProperties(device, dto);
            // 处理类型标签
            dto.setDeviceTypeLabel(getDeviceTypeLabel(device.getDeviceType()));
            return dto;
        }).collect(Collectors.toList());

        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    public List<UnifiedDeviceDTO> listInfoBoardsByDeviceIds(List<String> deviceIds) {
        List<String> normalizedIds = normalizeDeviceIds(deviceIds);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceType, "info_board")
               .in(UnifiedDevice::getDeviceId, normalizedIds);
        List<UnifiedDevice> devices = unifiedDeviceMapper.selectList(wrapper);
        if (devices == null || devices.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, UnifiedDeviceDTO> dtoByDeviceId = new HashMap<>();
        for (UnifiedDevice device : devices) {
            if (device == null || !StringUtils.hasText(device.getDeviceId())) {
                continue;
            }
            UnifiedDeviceDTO dto = new UnifiedDeviceDTO();
            BeanUtils.copyProperties(device, dto);
            dto.setDeviceTypeLabel(getDeviceTypeLabel(device.getDeviceType()));
            dtoByDeviceId.putIfAbsent(device.getDeviceId(), dto);
        }

        List<UnifiedDeviceDTO> result = new ArrayList<>();
        for (String deviceId : normalizedIds) {
            UnifiedDeviceDTO dto = dtoByDeviceId.get(deviceId);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private List<String> normalizeDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String deviceId : deviceIds) {
            if (!StringUtils.hasText(deviceId)) {
                continue;
            }
            normalized.add(deviceId.trim());
        }
        return new ArrayList<>(normalized);
    }

    private String getDeviceTypeLabel(String type) {
        if (type == null) return "未知设备";
        switch (type) {
            case "publish_server": return "信息发布服务器";
            case "publish_gateway": return "发布端加密网关";
            case "terminal_encrypt_gateway": return "终端加密网关";
            case "content_server": return "内容识别服务器";
            case "info_board": return "情报板";
            case "camera": return "摄像设备";
            default: return "其他设备 (" + type + ")";
        }
    }

    /**
     * 获取设备详情
     */
    @Override
    public DeviceDetailDTO getDeviceDetail(String deviceType, String deviceId) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceType, deviceType)
               .eq(UnifiedDevice::getDeviceId, deviceId);

        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);
        if (device == null) {
            return new DeviceDetailDTO();
        }

        DeviceDetailDTO detail = new DeviceDetailDTO();
        BeanUtils.copyProperties(device, detail);
        detail.setDeviceTypeLabel(getDeviceTypeLabel(device.getDeviceType()));

        // 处理特有属性 (从 JSON 解析)
        if (StringUtils.hasText(device.getExtraInfo())) {
            // 这里假设使用 Jackson 解析，实际项目中建议配置 ObjectMapper
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> specific = mapper.readValue(device.getExtraInfo(), Map.class);
                detail.setSpecificAttributes(specific);
            } catch (Exception e) {
                log.error("解析特有属性失败", e);
            }
        }

        return detail;
    }

    /**
     * 批量刷新设备状态
     */
    @Override
    public int batchRefreshStatus(BatchOperationDTO dto) {
        int successCount = 0;

        for (String deviceId : dto.getDeviceIds()) {
            try {
                boolean result = refreshSingleDeviceStatus(dto.getDeviceType(), deviceId);
                if (result) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("刷新设备状态失败: deviceType={}, deviceId={}", dto.getDeviceType(), deviceId, e);
            }
        }

        return successCount;
    }

    /**
     * 批量导出设备信息
     */
    @Override
    public void batchExport(BatchOperationDTO dto, HttpServletResponse response) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("设备信息");

        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {"设备ID", "设备名称", "设备类型", "IP地址", "端口", "状态", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // 填充数据
        int rowNum = 1;
        for (String deviceId : dto.getDeviceIds()) {
            try {
                DeviceDetailDTO detail = getDeviceDetail(dto.getDeviceType(), deviceId);
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(detail.getDeviceId());
                row.createCell(1).setCellValue(detail.getDeviceName());
                row.createCell(2).setCellValue(detail.getDeviceTypeLabel());
                row.createCell(3).setCellValue(detail.getIpAddress());
                row.createCell(4).setCellValue(detail.getPort() != null ? detail.getPort() : 0);
                row.createCell(5).setCellValue(detail.getStatus());
                row.createCell(6).setCellValue(detail.getCreateTime() != null ?
                    detail.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            } catch (Exception e) {
                log.error("导出设备信息失败: deviceId={}", deviceId, e);
            }
        }

        // 输出到响应
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = URLEncoder.encode("设备信息导出_" + System.currentTimeMillis(), StandardCharsets.UTF_8.toString());
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".xlsx");

        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 下发设备命令
     */
    @Override
    public Map<String, Object> sendCommand(DeviceCommandDTO dto) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("下发设备命令: deviceType={}, deviceId={}, commandType={}, operator={}",
                dto.getDeviceType(), dto.getDeviceId(), dto.getCommandType(), dto.getOperator());

            // 根据设备类型和命令类型执行相应操作
//            switch (dto.getCommandType()) {
//                case "reboot":
//                    handleRebootCommand(dto);
//                    break;
//                case "blackscreen":
//                    handleBlackscreenCommand(dto);
//                    break;
//                case "block_traffic":
//                    handleBlockTrafficCommand(dto);
//                    break;
//                case "restore":
//                    handleRestoreCommand(dto);
//                    break;
//                case "upgrade":
//                    handleUpgradeCommand(dto);
//                    break;
//                case "config":
//                    handleConfigCommand(dto);
//                    break;
//                default:
//                    throw new IllegalArgumentException("不支持的命令类型: " + dto.getCommandType());
//            }

            CommandHandler handler = commandHandlerFactory.getHandler(dto.getCommandType());
            if (handler == null) {
                throw new IllegalArgumentException("不支持的命令类型: " + dto.getCommandType());
            }
            handler.handle(dto);
            result.put("success", true);
            result.put("message", "命令下发成功");

        } catch (Exception e) {
            log.error("下发设备命令失败", e);
            result.put("success", false);
            result.put("message", "命令下发失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取设备统计信息
     */
    @Override
    public Map<String, Object> getDeviceStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        List<String> types = Arrays.asList("publish_server", "publish_gateway", "terminal_encrypt_gateway", "content_server", "info_board", "camera");
        long totalDevices = 0;
        long totalOnline = 0;

        for (String type : types) {
            long total = unifiedDeviceMapper.selectCount(new LambdaQueryWrapper<UnifiedDevice>().eq(UnifiedDevice::getDeviceType, type));
            long online = unifiedDeviceMapper.selectCount(new LambdaQueryWrapper<UnifiedDevice>()
                .eq(UnifiedDevice::getDeviceType, type)
                .and(w -> w.eq(UnifiedDevice::getStatus, "在线").or().eq(UnifiedDevice::getStatus, "正常")));

            Map<String, Object> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("online", online);
            statistics.put(type, stats);

            totalDevices += total;
            totalOnline += online;
        }

        statistics.put("totalDevices", totalDevices);
        statistics.put("totalOnline", totalOnline);

        return statistics;
    }

    @Override
    public void checkAllDeviceStatus() {
        log.info("开始统一检测所有设备状态");
        List<UnifiedDevice> deviceList = unifiedDeviceMapper.selectList(null);
        LocalDateTime now = LocalDateTime.now();

        for (UnifiedDevice device : deviceList) {
            try {
                // 情报板（info_board）状态由终端网关探测后上报，平台定时任务不介入
                if ("info_board".equals(device.getDeviceType())) {
                    log.debug("情报板 [{}] 跳过定时检测，由终端网关上报管理", device.getDeviceId());
                    continue;
                }

                boolean isOnline = false;

                // 网关设备（发布端加密网关 / 终端加密网关）：
                // 优先检查心跳（自动注册的设备通过 registry-client 上报心跳），无心跳时降级 TCP 探测
                if ("publish_gateway".equals(device.getDeviceType())
                        || "terminal_encrypt_gateway".equals(device.getDeviceType())) {
                    // 优先检查 Redis 心跳 key（由 registry-client 自动注册写入）
                    String heartTimeStr = stringRedisTemplate.opsForValue().get(HEARTBEAT_KEY_PREFIX + device.getDeviceId());
                    if (StringUtils.hasText(heartTimeStr)) {
                        isOnline = true;
                    } else if (device.getLastOnlineTime() != null &&
                               device.getLastOnlineTime().plusSeconds(HEARTBEAT_TIMEOUT).isAfter(now)) {
                        isOnline = true;
                    } else {
                        // 无心跳数据，降级到 TCP 端口探测
                        String ip = device.getIpAddress();
                        Integer probePort = device.getPort();
                        if (StringUtils.hasText(ip) && probePort != null) {
                            isOnline = probeTcpGateway(ip, probePort, 3000);
                            if (isOnline) {
                                device.setLastOnlineTime(now);
                            }
                            log.debug("[GatewayProbe] {} {}:{} -> {}",
                                    device.getDeviceType(), ip, probePort, isOnline ? "在线" : "离线");
                        } else {
                            log.warn("[GatewayProbe] 设备 [{}] IP/Port 未配置，跳过探测", device.getDeviceId());
                        }
                    }
                } else {
                    // 其他设备：优先从 Redis 获取心跳，否则判断最后在线时间是否超时
                    String heartTimeStr = stringRedisTemplate.opsForValue().get(HEARTBEAT_KEY_PREFIX + device.getDeviceId());
                    if (StringUtils.hasText(heartTimeStr)) {
                        isOnline = true;
                    } else if (device.getLastOnlineTime() != null &&
                               device.getLastOnlineTime().plusSeconds(HEARTBEAT_TIMEOUT).isAfter(now)) {
                        isOnline = true;
                    }
                }

                String currentStatus = device.getStatus();
                String targetStatus = isOnline ? "在线" : "离线";

                boolean needUpdate = !targetStatus.equals(currentStatus);
                if (needUpdate) {
                    device.setStatus(targetStatus);
                    device.setUpdateTime(now);
                    log.info("设备 [{}] 状态变更: {} -> {}", device.getDeviceId(), currentStatus, targetStatus);
                }
                // lastOnlineTime 可能已被网关探测逻辑更新，需一并持久化
                if (needUpdate || (isOnline && "publish_gateway".equals(device.getDeviceType()))
                        || (isOnline && "terminal_encrypt_gateway".equals(device.getDeviceType()))) {
                    unifiedDeviceMapper.updateById(device);
                }

                // 同步更新 Redis 状态缓存
                stringRedisTemplate.opsForValue().set(
                    STATUS_KEY_PREFIX + device.getDeviceId(),
                    targetStatus,
                    5,
                    TimeUnit.MINUTES
                );

            } catch (Exception e) {
                log.error("检测设备 [{}] 状态失败", device.getDeviceId(), e);
            }
        }
        log.info("所有设备状态检测完成，共检测 {} 个设备", deviceList.size());
    }

    /**
     * TCP 端口探测：连通返回 true，超时/拒绝返回 false
     *
     * @param ip        目标 IP
     * @param port      目标端口
     * @param timeoutMs 连接超时（毫秒）
     */
    private boolean probeTcpGateway(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            log.debug("[GatewayProbe] TCP 探测失败: {}:{} - {}", ip, port, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean receiveHeartbeat(DeviceHeartbeatDTO dto) {
        String deviceId = dto.getDeviceId();
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, deviceId);
        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);

        if (device == null) {
            log.warn("收到未知设备心跳: {}", deviceId);
            return false;
        }

        boolean needUpdate = false;
        LocalDateTime now = LocalDateTime.now();

        // 更新状态
        String newStatus = StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "在线";
        if (!newStatus.equals(device.getStatus())) {
            device.setStatus(newStatus);
            needUpdate = true;
        }

        // 更新心跳时间
        device.setLastOnlineTime(now);
        needUpdate = true;

        // 处理性能数据（如果有）
        if (dto.getExtraData() != null && !dto.getExtraData().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                // 合并原有的 extraInfo
                Map<String, Object> extraMap = new HashMap<>();
                if (StringUtils.hasText(device.getExtraInfo())) {
                    extraMap.putAll(mapper.readValue(device.getExtraInfo(), Map.class));
                }
                extraMap.putAll(dto.getExtraData());
                device.setExtraInfo(mapper.writeValueAsString(extraMap));
                needUpdate = true;
            } catch (Exception e) {
                log.error("处理心跳性能数据失败", e);
            }
        }

        if (needUpdate) {
            device.setUpdateTime(now);
            unifiedDeviceMapper.updateById(device);
        }

        // 更新 Redis 缓存
        stringRedisTemplate.opsForValue().set(
            HEARTBEAT_KEY_PREFIX + deviceId,
            String.valueOf(System.currentTimeMillis()),
            70,
            TimeUnit.SECONDS
        );

        stringRedisTemplate.opsForValue().set(
            STATUS_KEY_PREFIX + deviceId,
            newStatus,
            5,
            TimeUnit.MINUTES
        );

        log.debug("收到设备 [{}] 的心跳", deviceId);
        return true;
    }

    // ========== 统一 CRUD 实现 ==========

    @Override
    public boolean addDevice(UnifiedDevice device) {
        return unifiedDeviceMapper.insert(device) > 0;
    }

    @Override
    public boolean updateDevice(UnifiedDevice device) {
        // 优先使用 id 更新，如果没有 id 则通过 deviceId 查找
        if (device.getId() != null) {
            return unifiedDeviceMapper.updateById(device) > 0;
        }

        // 通过 deviceId 查找并更新
        if (device.getDeviceId() != null) {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceId, device.getDeviceId());
            UnifiedDevice existing = unifiedDeviceMapper.selectOne(wrapper);
            if (existing != null) {
                device.setId(existing.getId());
                return unifiedDeviceMapper.updateById(device) > 0;
            }
        }
        return false;
    }

    @Override
    public boolean deleteDevice(String deviceId) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, deviceId);
        return unifiedDeviceMapper.delete(wrapper) > 0;
    }

    @Override
    public boolean deleteDeviceById(Long id) {
        return unifiedDeviceMapper.deleteById(id) > 0;
    }

    @Override
    public UnifiedDevice getDeviceById(Long id) {
        return unifiedDeviceMapper.selectById(id);
    }

    @Override
    public boolean updateStatusByIp(String ip, String status) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getIpAddress, ip)
               .eq(UnifiedDevice::getDeviceType, "info_board");
        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);
        if (device == null) {
            log.warn("通过IP未找到情报板设备: ip={}", ip);
            return false;
        }
        device.setStatus(status);
        device.setUpdateTime(LocalDateTime.now());
        int rows = unifiedDeviceMapper.updateById(device);
        log.info("情报板状态更新: ip={}, status={}, rows={}", ip, status, rows);
        return rows > 0;
    }

    /**
     * 获取所有情报板地图点位（不分页）
     */
    @Override
    public Map<String, Object> getInfoBoardStatusByIp(String ip) {
        Map<String, Object> result = new HashMap<>();
        if (!StringUtils.hasText(ip)) {
            return result;
        }
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getIpAddress, ip.trim())
               .eq(UnifiedDevice::getDeviceType, "info_board");
        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);
        if (device == null) {
            log.debug("[InfoBoardStatus] info board not found by ip={}", ip);
            return result;
        }
        String status = device.getStatus();
        result.put("ip", device.getIpAddress());
        result.put("port", device.getPort());
        result.put("deviceId", device.getDeviceId());
        result.put("deviceName", device.getDeviceName());
        result.put("status", status);
        result.put("online", isInfoBoardOnlineStatus(status));
        result.put("updateTime", device.getUpdateTime());
        return result;
    }

    @Override
    public List<Map<String, Object>> getInfoBoardMapPoints() {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceType, "info_board")
               .select(UnifiedDevice::getId,
                       UnifiedDevice::getDeviceId,
                       UnifiedDevice::getDeviceName,
                       UnifiedDevice::getIpAddress,
                       UnifiedDevice::getLongitude,
                       UnifiedDevice::getLatitude,
                       UnifiedDevice::getStatus)
               .orderByAsc(UnifiedDevice::getId);

        List<UnifiedDevice> devices = unifiedDeviceMapper.selectList(wrapper);

        return devices.stream().map(device -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", device.getId());
            point.put("deviceId", device.getDeviceId());
            point.put("deviceName", device.getDeviceName());
            point.put("ipAddress",device.getIpAddress());
            point.put("longitude", device.getLongitude());
            point.put("latitude", device.getLatitude());
            point.put("status", device.getStatus());
            return point;
        }).collect(Collectors.toList());
    }

    /**
     * 通过 IP 查询情报板经纬度
     */
    @Override
    public boolean updateStatusByIpSkipAlarm(String ip, String status) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getIpAddress, ip)
               .eq(UnifiedDevice::getDeviceType, "info_board");
        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);
        if (device == null) {
            log.warn("[BatchStatus] 通过IP未找到情报板设备: ip={}", ip);
            return false;
        }
        // 告警状态由告警服务统一管理，探测上报不覆盖
        if ("告警".equals(device.getStatus())) {
            log.debug("[BatchStatus] 情报板 {} 当前为告警状态，跳过探测覆盖", ip);
            return false;
        }
        device.setStatus(status);
        device.setUpdateTime(LocalDateTime.now());
        int rows = unifiedDeviceMapper.updateById(device);
        log.info("[BatchStatus] 情报板状态更新（探测上报）: ip={}, status={}, rows={}", ip, status, rows);
        return rows > 0;
    }

    @Override
    public boolean updateDeviceLocation(String deviceId, Double longitude, Double latitude) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, deviceId)
               .eq(UnifiedDevice::getDeviceType, "info_board");
        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);
        if (device == null) {
            log.warn("[updateLocation] 设备不存在或非情报板类型: deviceId={}", deviceId);
            return false;
        }
        device.setLongitude(longitude);
        device.setLatitude(latitude);
        device.setUpdateTime(LocalDateTime.now());
        int rows = unifiedDeviceMapper.updateById(device);
        log.info("[updateLocation] 经纬度更新: deviceId={}, longitude={}, latitude={}, rows={}", deviceId, longitude, latitude, rows);
        return rows > 0;
    }

    @Override
    public boolean updateDeviceIp(String deviceId, String ip) {
        LambdaUpdateWrapper<UnifiedDevice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, deviceId)
               .set(UnifiedDevice::getIpAddress, ip)
               .set(UnifiedDevice::getUpdateTime, LocalDateTime.now());
        int rows = unifiedDeviceMapper.update(null, wrapper);
        log.info("[updateDeviceIp] 设备IP更新: deviceId={}, ip={}, rows={}", deviceId, ip, rows);
        return rows > 0;
    }

    @Override
    public UnifiedDevice findByDeviceId(String deviceId) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, deviceId);
        return unifiedDeviceMapper.selectOne(wrapper);
    }

    @Override
    public boolean updateExtraInfo(String deviceId, String extraInfoJson) {
        LambdaUpdateWrapper<UnifiedDevice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UnifiedDevice::getDeviceId, deviceId)
               .set(UnifiedDevice::getExtraInfo, extraInfoJson)
               .set(UnifiedDevice::getUpdateTime, LocalDateTime.now());
        int rows = unifiedDeviceMapper.update(null, wrapper);
        log.info("[updateExtraInfo] deviceId={}, rows={}", deviceId, rows);
        return rows > 0;
    }

    @Override
    public Map<String, Object> getInfoBoardLocationByIp(String ip) {
        LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnifiedDevice::getIpAddress, ip)
               .eq(UnifiedDevice::getDeviceType, "info_board")
               .select(UnifiedDevice::getLongitude, UnifiedDevice::getLatitude);
        UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);
        Map<String, Object> result = new HashMap<>();
        if (device != null) {
            result.put("longitude", device.getLongitude());
            result.put("latitude", device.getLatitude());
        }
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private boolean isInfoBoardOnlineStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim();
        return "在线".equals(normalized) || "正常".equals(normalized) || "告警".equals(normalized);
    }

    private boolean refreshSingleDeviceStatus(String deviceType, String deviceId) {
        // 实际项目中应该调用设备接口获取实时状态
        // 这里简化处理
        log.info("刷新设备状态: deviceType={}, deviceId={}", deviceType, deviceId);
        return true;
    }

    private void handleRebootCommand(DeviceCommandDTO dto) {
        // TODO: 调用设备重启接口
        log.info("执行设备重启命令: {}", dto);
    }

    private void handleBlackscreenCommand(DeviceCommandDTO dto) {
        // TODO: 调用黑屏命令接口
        log.info("执行黑屏命令: {}", dto);
    }

    private void handleBlockTrafficCommand(DeviceCommandDTO dto) {
        // TODO: 调用流量阻断接口
        log.info("执行流量阻断命令: {}", dto);
    }

    private void handleRestoreCommand(DeviceCommandDTO dto) {
        // TODO: 调用恢复命令接口
        log.info("执行恢复命令: {}", dto);
    }

    private void handleUpgradeCommand(DeviceCommandDTO dto) {
        // TODO: 调用升级命令接口
        log.info("执行升级命令: {}", dto);
    }

    private void handleConfigCommand(DeviceCommandDTO dto) {
        // TODO: 调用配置下发接口
        log.info("执行配置命令: {}", dto);
    }
}
