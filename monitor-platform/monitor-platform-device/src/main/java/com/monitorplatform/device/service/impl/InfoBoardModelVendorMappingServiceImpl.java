package com.monitorplatform.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.device.entity.InfoBoardModelVendorMapping;
import com.monitorplatform.device.mapper.InfoBoardModelVendorMappingMapper;
import com.monitorplatform.device.service.InfoBoardModelVendorMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 情报板型号-厂商映射服务实现
 */
@Slf4j
@Service
public class InfoBoardModelVendorMappingServiceImpl implements InfoBoardModelVendorMappingService {

    @Resource
    private InfoBoardModelVendorMappingMapper mapper;

    @Override
    public List<InfoBoardModelVendorMapping> listAll() {
        LambdaQueryWrapper<InfoBoardModelVendorMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InfoBoardModelVendorMapping::getEnabled, true)
               .orderByAsc(InfoBoardModelVendorMapping::getModelCode);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<InfoBoardModelVendorMapping> listAllIncludingDisabled() {
        LambdaQueryWrapper<InfoBoardModelVendorMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(InfoBoardModelVendorMapping::getModelCode);
        return mapper.selectList(wrapper);
    }

    @Override
    public InfoBoardModelVendorMapping getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public Optional<InfoBoardModelVendorMapping> findByModelCode(String modelCode) {
        LambdaQueryWrapper<InfoBoardModelVendorMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InfoBoardModelVendorMapping::getModelCode, modelCode)
               .eq(InfoBoardModelVendorMapping::getEnabled, true);
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public boolean add(InfoBoardModelVendorMapping mapping) {
        mapping.setCreateTime(LocalDateTime.now());
        mapping.setUpdateTime(LocalDateTime.now());
        if (mapping.getEnabled() == null) {
            mapping.setEnabled(true);
        }
        return mapper.insert(mapping) > 0;
    }

    @Override
    public boolean update(InfoBoardModelVendorMapping mapping) {
        mapping.setUpdateTime(LocalDateTime.now());
        return mapper.updateById(mapping) > 0;
    }

    @Override
    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }
}
