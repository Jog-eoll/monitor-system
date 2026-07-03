package com.monitorplatform.device.service;

import com.monitorplatform.device.entity.InfoBoardModelVendorMapping;

import java.util.List;
import java.util.Optional;

/**
 * 情报板型号-厂商映射服务接口
 */
public interface InfoBoardModelVendorMappingService {

    /**
     * 查询所有映射（仅返回启用的）
     */
    List<InfoBoardModelVendorMapping> listAll();

    /**
     * 查询所有映射（含禁用）
     */
    List<InfoBoardModelVendorMapping> listAllIncludingDisabled();

    /**
     * 根据ID查询
     */
    InfoBoardModelVendorMapping getById(Long id);

    /**
     * 根据型号编码查询（仅启用）
     */
    Optional<InfoBoardModelVendorMapping> findByModelCode(String modelCode);

    /**
     * 新增映射
     */
    boolean add(InfoBoardModelVendorMapping mapping);

    /**
     * 更新映射
     */
    boolean update(InfoBoardModelVendorMapping mapping);

    /**
     * 删除映射
     */
    boolean delete(Long id);
}
