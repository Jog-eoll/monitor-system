package com.monitorplatform.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 统一设备管理服务接口
 */
public interface UnifiedDeviceService {
    
    /**
     * 获取设备分类树
     * @return 设备树结构
     */
    List<DeviceTreeNodeDTO> getDeviceTree();
    
    /**
     * 统一分页查询所有设备
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param deviceType 设备类型（可选）
     * @param status 状态（可选）
     * @param keyword 关键词搜索（可选）
     * @return 分页结果
     */
    Page<UnifiedDeviceDTO> pageListAllDevices(Integer pageNum, Integer pageSize, 
                                               String deviceType, String status, String keyword);

    /**
     * Batch query info board devices by deviceId.
     */
    List<UnifiedDeviceDTO> listInfoBoardsByDeviceIds(List<String> deviceIds);
    
    /**
     * 获取设备详情（含监控数据）
     * @param deviceType 设备类型
     * @param deviceId 设备ID
     * @return 设备详情
     */
    DeviceDetailDTO getDeviceDetail(String deviceType, String deviceId);
    
    /**
     * 批量刷新设备状态
     * @param dto 批量操作DTO
     * @return 成功刷新的设备数量
     */
    int batchRefreshStatus(BatchOperationDTO dto);
    
    /**
     * 批量导出设备信息
     * @param dto 批量操作DTO
     * @param response HTTP响应
     */
    void batchExport(BatchOperationDTO dto, HttpServletResponse response) throws Exception;
    
    /**
     * 下发设备命令
     * @param dto 命令DTO
     * @return 执行结果
     */
    Map<String, Object> sendCommand(DeviceCommandDTO dto);
    
    /**
     * 获取设备统计信息
     * @return 统计数据
     */
    Map<String, Object> getDeviceStatistics();

    /**
     * 统一检测所有设备状态（由定时任务调用）
     */
    void checkAllDeviceStatus();

    /**
     * 接收设备心跳
     */
    boolean receiveHeartbeat(DeviceHeartbeatDTO heartbeatDTO);

    // ========== 统一 CRUD 接口 ==========

    /**
     * 新增设备
     */
    boolean addDevice(UnifiedDevice device);

    /**
     * 修改设备
     */
    boolean updateDevice(UnifiedDevice device);

    /**
     * 删除设备（通过 deviceId）
     */
    boolean deleteDevice(String deviceId);

    /**
     * 删除设备（通过主键 id）
     */
    boolean deleteDeviceById(Long id);

    /**
     * 根据ID查询设备
     */
    UnifiedDevice getDeviceById(Long id);

    /**
     * 通过 IP 地址更新情报板状态
     * 用于告警联动：告警触发时设为"告警"，告警全部处理后恢复"在线"
     *
     * @param ip     情报板 IP
     * @param status 目标状态
     * @return 是否找到并更新成功
     */
    boolean updateStatusByIp(String ip, String status);

    /**
     * Query info board status by IP.
     *
     * @param ip info board IP
     * @return status view, empty map when not found
     */
    Map<String, Object> getInfoBoardStatusByIp(String ip);

    /**
     * 获取所有情报板地图点位（不分页）
     * 用于地图展示，返回 id、deviceId、deviceName、longitude、latitude、status
     *
     * @return 情报板点位列表
     */
    List<Map<String, Object>> getInfoBoardMapPoints();

    /**
     * 通过 IP 查询情报板经纬度
     * 用于告警推送时附带地理位置信息
     *
     * @param ip 情报板 IP
     * @return { longitude, latitude }，未找到时返回空 Map
     */
    Map<String, Object> getInfoBoardLocationByIp(String ip);

    /**
     * 通过 IP 更新情报板状态（跳过告警状态，由告警服务统一管理）
     * 用于终端网关探测结果上报批量更新时，避免覆盖告警状态
     *
     * @param ip     情报板 IP
     * @param status 目标状态（"在线" / "离线"）
     * @return true-已更新, false-设备不存在或当前为告警状态跳过
     */
    boolean updateStatusByIpSkipAlarm(String ip, String status);

    /**
     * 更新设备经纬度（地图拖拽定位保存）
     * 仅更新 longitude / latitude / updateTime，不触碰 status
     *
     * @param deviceId  设备唯一标识（deviceId）
     * @param longitude 经度
     * @param latitude  纬度
     * @return true-更新成功, false-设备不存在或非情报板类型
     */
    boolean updateDeviceLocation(String deviceId, Double longitude, Double latitude);

    boolean updateDeviceIp(String deviceId, String ip);

    /**
     * 根据 deviceId 查询设备
     */
    UnifiedDevice findByDeviceId(String deviceId);

    /**
     * 更新设备的 extraInfo（JSON 字段）
     */
    boolean updateExtraInfo(String deviceId, String extraInfoJson);
}
