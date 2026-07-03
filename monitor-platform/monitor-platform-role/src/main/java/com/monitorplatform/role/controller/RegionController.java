package com.monitorplatform.role.controller;


import com.monitorplatform.common.entity.Result;
import com.monitorplatform.role.entity.Region;
import com.monitorplatform.role.entity.dto.*;
import com.monitorplatform.role.service.RegionService;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/region")
public class RegionController {

    private final RegionService regionService;
    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }
    @ApiOperation("创建区域")
    @PostMapping("/create")
    public Result<?> create(@Validated @RequestBody RegionCreateReq req) {
        try {
            Region region = regionService.create(req);
            return Result.success("创建成功", region);
        } catch (Exception e) {
            return Result.error("创建失败", e);
        }
    }
    @GetMapping("/get")
    public Result<?> get(@RequestParam Long id) {
        try {
            return Result.success(regionService.getById(id));
        } catch (Exception e) {
            return Result.error("查询失败", e);
        }
    }
    @ApiOperation("更新区域")
    @PostMapping("/update")
    public Result<?> update(@Validated @RequestBody RegionUpdateReq req) {
        try {
            Region region = regionService.update(req);
            return Result.success("更新成功", region);
        } catch (Exception e) {
            return Result.error("更新失败", e);
        }
    }
    @ApiOperation("删除区域")
    @PostMapping("/delete")
    public Result<?> delete(@Validated @RequestBody RegionDeleteReq req) {
        try {
            regionService.delete(req.getId());
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("删除失败", e);
        }
    }
    /** 区域树 */
    @GetMapping("/tree")
    public Result<?> tree() {
        try {
            List<RegionTreeNodeResp> tree = regionService.tree();
            return Result.success(tree);
        } catch (Exception e) {
            return Result.error("查询区域树失败", e);
        }
    }
    /** 扁平分页（增删改查里的“查列表”） */
    @GetMapping("/page")
    public Result<?> page(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            RegionPageResp resp = regionService.page(name, code, page, pageSize);
            return Result.success(resp);
        } catch (Exception e) {
            return Result.error("分页查询失败", e);
        }
    }

    /** 升级：与父同级 */
    @ApiOperation("升级区域")
    @PostMapping("/promote")
    public Result<?> promote(@Validated @RequestBody RegionIdReq req) {
        try {
            Region region = regionService.promote(req.getId());
            return Result.success("升级成功", region);
        } catch (Exception e) {
            return Result.error("升级失败", e);
        }
    }

    /** 降级：成为前一个同级兄弟的子节点 */
    @ApiOperation("降级区域")
    @PostMapping("/demote")
    public Result<?> demote(@Validated @RequestBody RegionIdReq req) {
        try {
            Region region = regionService.demote(req.getId());
            return Result.success("降级成功", region);
        } catch (Exception e) {
            return Result.error("降级失败", e);
        }
    }

    /**
     * 全量树覆盖：先清空 region 表，再按树结构重建。
     * Body: { "roots": [ { "id":1, "name":"", "code":"", "sort":0, "children":[...] }, ... ] }
     */
    @ApiOperation("替换区域树")
    @PostMapping("/replace-tree")
    public Result<?> replaceTree(@Validated @RequestBody RegionTreeReplaceReq req) {
        try {
            regionService.replaceFullTree(req);
            return Result.success("全量覆盖成功", null);
        } catch (Exception e) {
            return Result.error("全量覆盖失败", e);
        }
    }

    @GetMapping("/region-device-tree")
    public Result<?> regionDeviceTree() {
        try {
            List<RegionDeviceTreeNodeResp> tree = regionService.regionDeviceTree();
            return Result.success(tree);
        } catch (Exception e) {
            return Result.error("查询区域设备树失败", e);
        }
    }
}
