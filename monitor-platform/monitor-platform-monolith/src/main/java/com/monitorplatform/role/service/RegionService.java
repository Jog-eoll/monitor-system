package com.monitorplatform.role.service;

/*
区域服务
*/

import com.monitorplatform.role.entity.Region;
import com.monitorplatform.role.entity.dto.*;

import java.util.List;

public interface RegionService {

    Region create(RegionCreateReq req);

    Region update(RegionUpdateReq req);

    void delete(Long id);

    Region getById(Long id);

    /** 区域树（整棵，常用于级联选择器） */
    List<RegionTreeNodeResp> tree();

    /** 扁平分页（常用于表格维护） */
    RegionPageResp page(String name, String code, Integer page, Integer pageSize);

    /** 升级：与父节点同级（父的父为新的 parent；无父则失败） */
    Region promote(Long id);

    /** 降级：成为「前一个同级兄弟」的最后一个子节点；已是同级第一个则失败 */
    Region demote(Long id);

    /**
     * 用前端提交的整棵树覆盖 region 表（先删后插）。
     */
    void replaceFullTree(RegionTreeReplaceReq req);

    /**
     * 查询区域设备树
     * @return
     */
    List<RegionDeviceTreeNodeResp> regionDeviceTree();
}
