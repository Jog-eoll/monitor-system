package com.monitorplatform.device.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.device.entity.InfoBoardModelVendorMapping;
import com.monitorplatform.device.service.InfoBoardModelVendorMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 情报板型号-厂商映射维护接口
 */
@Slf4j
@RestController
@RequestMapping("/device/info-board/model-vendor-mappings")
public class InfoBoardModelVendorController {

    @Resource
    private InfoBoardModelVendorMappingService mappingService;

    /**
     * 查询所有映射（仅返回启用的）
     */
    @GetMapping
    public Result<List<InfoBoardModelVendorMapping>> listAll() {
        try {
            List<InfoBoardModelVendorMapping> mappings = mappingService.listAll();
            return Result.data(mappings);
        } catch (Exception e) {
            log.error("查询型号厂商映射失败", e);
            return Result.error("查询映射失败: " + e.getMessage());
        }
    }

    /**
     * 新增映射
     */
    @PostMapping
    public Result<InfoBoardModelVendorMapping> add(@Valid @RequestBody InfoBoardModelVendorMapping mapping) {
        try {
            // 校验 modelCode 不重复
            if (mappingService.findByModelCode(mapping.getModelCode()).isPresent()) {
                return Result.error("型号编码已存在: " + mapping.getModelCode());
            }
            boolean ok = mappingService.add(mapping);
            return ok ? Result.data(mapping) : Result.error("新增映射失败");
        } catch (Exception e) {
            log.error("新增型号厂商映射失败", e);
            return Result.error("新增映射失败: " + e.getMessage());
        }
    }

    /**
     * 更新映射
     */
    @PutMapping("/{id}")
    public Result<InfoBoardModelVendorMapping> update(@PathVariable Long id,
                                                       @Valid @RequestBody InfoBoardModelVendorMapping mapping) {
        try {
            InfoBoardModelVendorMapping existing = mappingService.getById(id);
            if (existing == null) {
                return Result.error("映射不存在: id=" + id);
            }
            mapping.setId(id);
            boolean ok = mappingService.update(mapping);
            return ok ? Result.data(mapping) : Result.error("更新映射失败");
        } catch (Exception e) {
            log.error("更新型号厂商映射失败: id={}", id, e);
            return Result.error("更新映射失败: " + e.getMessage());
        }
    }

    /**
     * 删除映射
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            boolean ok = mappingService.delete(id);
            return ok ? Result.success("删除成功") : Result.error("映射不存在: id=" + id);
        } catch (Exception e) {
            log.error("删除型号厂商映射失败: id={}", id, e);
            return Result.error("删除映射失败: " + e.getMessage());
        }
    }
}
