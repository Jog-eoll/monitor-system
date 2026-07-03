package com.monitorplatform.role.service.impl;

import com.monitorplatform.role.entity.Region;
import com.monitorplatform.role.entity.dto.*;
import com.monitorplatform.role.mapper.RegionMapper;
import com.monitorplatform.role.mapper.UnifiedDeviceInfoMapper;
import com.monitorplatform.role.service.RegionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/*
区域具体方法实现

*/
@Service
public class RegionServiceImpl implements RegionService {

    private static final String NODE_REGION = "REGION";
    private static final String NODE_DEVICE = "DEVICE";

    private final RegionMapper regionMapper;
    private final UnifiedDeviceInfoMapper unifiedDeviceInfoMapper;

    public RegionServiceImpl(RegionMapper regionMapper, UnifiedDeviceInfoMapper unifiedDeviceInfoMapper) {
        this.regionMapper = regionMapper;
        this.unifiedDeviceInfoMapper = unifiedDeviceInfoMapper;
    }

    //  创建区域
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Region create(RegionCreateReq req) {

        String name = trim(req.getName());
        String code = trim(req.getCode());

        validateRequired(name, "名称不能为空");
        validateRequired(code, "编码不能为空");

        if (regionMapper.selectByCode(code) != null) {
            throw new RuntimeException("区域编码已存在：" + code);
        }

        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        Integer maxSort = regionMapper.selectMaxSortByParentId(parentId);
        int nextSort = (maxSort == null ? 0 : maxSort) + 1;
        int sort = req.getSort()==null?nextSort:req.getSort();

        Region region = new Region();
        region.setCode(code);
        region.setName(name);
        region.setParentId(parentId);
        region.setSort(sort);
        regionMapper.insert(region);
        return regionMapper.selectById(region.getId());

    }

    //  更新区域
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Region update(RegionUpdateReq req) {

        Region old = regionMapper.selectById(req.getId());
        if (old == null) {
            throw new RuntimeException("记录不存在："+req.getId());
        }

        String newName = req.getName() == null ? old.getName() : trim(req.getName());
        String newCode = req.getCode() == null ? old.getCode() : trim(req.getCode());
        Long newParentId = req.getParentId() == null ? old.getParentId() : req.getParentId();
        Integer newSort = req.getSort() == null ? old.getSort() : req.getSort();


        validateRequired(newName, "名称不能为空");
        validateRequired(newCode, "编码不能为空");
        Region sameCode = regionMapper.selectByCode(newCode);

        if (sameCode != null && !sameCode.getId().equals(old.getId())) {
            throw new RuntimeException("区域编码已存在：" + newCode);
        }
        if (newParentId.equals(req.getId())) {
            throw new RuntimeException("父节点不能为自身");
        }
        if (isDescendant(req.getId(), newParentId)) {
            throw new RuntimeException("父节点不能为当前节点的子节点");
        }

        Region toUpdate = new Region();
        toUpdate.setId(old.getId());
        toUpdate.setName(newName);
        toUpdate.setCode(newCode);
        toUpdate.setParentId(newParentId);
        toUpdate.setSort(newSort);
        regionMapper.updateById(toUpdate);
        return regionMapper.selectById(old.getId());
    }

    //  删除区域
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Region old = regionMapper.selectById(id);
        if (old == null) {
            throw new RuntimeException("记录不存在：" + id);
        }
        List<Long> subtreeIds = collectSubtreeIdsIncludingSelf(id);
        if (subtreeIds.isEmpty()) {
            return;
        }
        regionMapper.deleteByIds(subtreeIds);
    }

    /** 自顶向下 BFS 收集子树所有 id（含根） */
    private List<Long> collectSubtreeIdsIncludingSelf(Long rootId) {
        List<Long> order = new ArrayList<>();
        Deque<Long> q = new ArrayDeque<>();
        q.add(rootId);
        while (!q.isEmpty()) {
            Long cur = q.poll();
            order.add(cur);
            for (Region c : regionMapper.selectByParentId(cur)) {
                q.add(c.getId());
            }
        }
        return order;
    }

    //  查询
    @Override
    public Region getById(Long id) {
        Region r = regionMapper.selectById(id);
        if (r == null) {
            throw new RuntimeException("记录不存在：" + id);
        }
        return r;
    }

    //  获取节点树
    @Override
    public List<RegionTreeNodeResp> tree() {
        List<Region> all = regionMapper.selectAllOrderByTree();
        Map<Long, RegionTreeNodeResp> map = new LinkedHashMap<>();
        List<RegionTreeNodeResp> roots = new ArrayList<>();
        for (Region r : all) {
            map.put(r.getId(), toNode(r));
        }
        for (Region r : all) {
            RegionTreeNodeResp node = map.get(r.getId());
            Long parentId = r.getParentId() == null ? 0L : r.getParentId();
            if (parentId == 0L) {
                roots.add(node);
            } else {
                RegionTreeNodeResp parent = map.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    //  获取分页
    @Override
    public RegionPageResp page(String name, String code, Integer page, Integer pageSize) {
        int pNo = (page == null || page < 1) ? 1 : page;
        int pSize = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
        int offset = (pNo - 1) * pSize;
        String nameLike = blankToNull(trim(name));
        String codeLike = blankToNull(trim(code));
        long total = regionMapper.count(nameLike, codeLike);
        List<Region> records = regionMapper.selectPage(nameLike, codeLike, offset, pSize);
        RegionPageResp resp = new RegionPageResp();
        resp.setTotal(total);
        resp.setRecords(records);
        return resp;
    }

    //  转化节点
    private RegionTreeNodeResp toNode(Region r) {
        RegionTreeNodeResp n = new RegionTreeNodeResp();
        n.setId(r.getId());
        n.setName(r.getName());
        n.setCode(r.getCode());
        n.setSort(r.getSort());
        n.setParentId(r.getParentId());
        return n;
    }

    /** 判断 targetParentId 是否是 nodeId 的子树中的节点（含多级） */
    private boolean isDescendant(Long nodeId, Long targetParentId) {
        Deque<Long> q = new ArrayDeque<>();
        for (Region c : regionMapper.selectByParentId(nodeId)) {
            q.add(c.getId());
        }
        while (!q.isEmpty()) {
            Long cur = q.poll();
            if (cur.equals(targetParentId)) {
                return true;
            }
            for (Region c : regionMapper.selectByParentId(cur)) {
                q.add(c.getId());
            }
        }
        return false;
    }

    private void validateRequired(String val, String msg) {
        if (val == null || val.isEmpty()) {
            throw new RuntimeException(msg);
        }
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String blankToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    //  升级
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Region promote(Long id) {
        Region node = regionMapper.selectById(id);
        if (node == null) {
            throw new RuntimeException("记录不存在：" + id);
        }
        Long oldParentId = node.getParentId() == null ? 0L : node.getParentId();
        if (oldParentId == 0L) {
            throw new RuntimeException("已在顶级，无法升级");
        }
        Region parent = regionMapper.selectById(oldParentId);
        if (parent == null) {
            throw new RuntimeException("父节点不存在：" + oldParentId);
        }
        Long newParentId = parent.getParentId() == null ? 0L : parent.getParentId();

        Region toUpdate = new Region();
        toUpdate.setId(node.getId());
        toUpdate.setName(node.getName());
        toUpdate.setCode(node.getCode());
        toUpdate.setParentId(newParentId);
        toUpdate.setSort(nextSortUnderParent(newParentId));
        regionMapper.updateById(toUpdate);
        return regionMapper.selectById(id);
    }

    //  降级
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Region demote(Long id) {
        Region node = regionMapper.selectById(id);
        if (node == null) {
            throw new RuntimeException("记录不存在：" + id);
        }
        Long parentId = node.getParentId() == null ? 0L : node.getParentId();
        List<Region> siblings = regionMapper.selectByParentId(parentId);
        Region prev = null;
        for (Region s : siblings) {
            if (s.getId().equals(id)) {
                break;
            }
            prev = s;
        }
        if (prev == null) {
            throw new RuntimeException("无法降级：当前已是同级中的第一个节点");
        }
        Long newParentId = prev.getId();

        Region toUpdate = new Region();
        toUpdate.setId(node.getId());
        toUpdate.setName(node.getName());
        toUpdate.setCode(node.getCode());
        toUpdate.setParentId(newParentId);
        toUpdate.setSort(nextSortUnderParent(newParentId));
        regionMapper.updateById(toUpdate);
        return regionMapper.selectById(id);
    }

    private int nextSortUnderParent(Long parentId) {
        Integer max = regionMapper.selectMaxSortByParentId(parentId);
        return (max == null ? 0 : max) + 1;
    }

    //  全量覆盖

    private static final int MAX_REPLACE_NODES = 5000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceFullTree(RegionTreeReplaceReq req) {
        if (req == null || req.getRoots() == null || req.getRoots().isEmpty()) {
            throw new RuntimeException("roots不能为空");
        }
        validateSnapshotTree(req.getRoots());

        regionMapper.deleteAll();

        for (RegionSnapshotNodeReq root : req.getRoots()) {
            insertSnapshotRecursive(root, 0L);
        }
    }

    private void insertSnapshotRecursive(RegionSnapshotNodeReq node, long parentDbId) {
        String name = trim(node.getName());
        String code = trim(node.getCode());
        validateRequired(name, "名称不能为空");
        validateRequired(code, "编码不能为空");

        int sort = node.getSort() == null ? 0 : node.getSort();

        Region r = new Region();
        r.setName(name);
        r.setCode(code);
        r.setSort(sort);
        r.setParentId(parentDbId);

        if (node.getId() != null) {
            if (node.getId() <= 0) {
                throw new RuntimeException("id 必须大于0，或留空走自增");
            }
            r.setId(node.getId());
            regionMapper.insertWithId(r);
        } else {
            regionMapper.insert(r);
        }

        long dbId = r.getId();
        List<RegionSnapshotNodeReq> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (RegionSnapshotNodeReq ch : children) {
            insertSnapshotRecursive(ch, dbId);
        }
    }

    private void validateSnapshotTree(List<RegionSnapshotNodeReq> roots) {
        Set<String> codes = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        int[] count = new int[]{0};
        for (RegionSnapshotNodeReq r : roots) {
            walkValidate(r, codes, ids, count);
        }
        if (count[0] > MAX_REPLACE_NODES) {
            throw new RuntimeException("节点数量过多，单次最多允许：" + MAX_REPLACE_NODES);
        }
    }

    private void walkValidate(RegionSnapshotNodeReq n, Set<String> codes, Set<Long> ids, int[] count) {
        if (n == null) {
            throw new RuntimeException("存在空节点");
        }
        count[0]++;
        String code = trim(n.getCode());
        validateRequired(code, "编码不能为空");
        if (!codes.add(code)) {
            throw new RuntimeException("树内编码重复：" + code);
        }
        if (n.getId() != null) {
            if (n.getId() <= 0) {
                throw new RuntimeException("id 必须大于0，或留空走自增");
            }
            if (!ids.add(n.getId())) {
                throw new RuntimeException("树内 id 重复：" + n.getId());
            }
        }
        List<RegionSnapshotNodeReq> children = n.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        for (RegionSnapshotNodeReq c : children) {
            walkValidate(c, codes, ids, count);
        }
    }

    //  查区域设备节点数
    @Override
    public List<RegionDeviceTreeNodeResp> regionDeviceTree() {
        List<Region> allRegions = regionMapper.selectAllOrderByTree();
        Map<Long, RegionDeviceTreeNodeResp> regionNodeMap = new LinkedHashMap<>();
        List<RegionDeviceTreeNodeResp> roots = new ArrayList<>();

        for (Region r : allRegions) {
            regionNodeMap.put(r.getId(), toRegionNode(r));
        }
        for (Region r : allRegions) {
            RegionDeviceTreeNodeResp node = regionNodeMap.get(r.getId());
            Long parentId = r.getParentId() == null ? 0L : r.getParentId();
            if (parentId == 0L) {
                roots.add(node);
            } else {
                RegionDeviceTreeNodeResp parent = regionNodeMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        List<UnifiedDeviceInfoTreeRow> deviceRows = unifiedDeviceInfoMapper.selectAllForRegionDeviceTree();
        Map<Long, List<UnifiedDeviceInfoTreeRow>> byRegionId = new HashMap<>();
        for (UnifiedDeviceInfoTreeRow row : deviceRows) {
            Long regionId = row.getRegionId();
            if (regionId == null) {
                continue;
            }
            byRegionId.computeIfAbsent(regionId, k -> new ArrayList<>()).add(row);
        }

        for (Map.Entry<Long, List<UnifiedDeviceInfoTreeRow>> e : byRegionId.entrySet()) {
            RegionDeviceTreeNodeResp regionNode = regionNodeMap.get(e.getKey());
            if (regionNode == null) {
                continue;
            }
            int order = 0;
            for (UnifiedDeviceInfoTreeRow row : e.getValue()) {
                regionNode.getChildren().add(toDeviceNode(row, order++));
            }
        }

        return roots;
    }

    private RegionDeviceTreeNodeResp toRegionNode(Region r) {
        RegionDeviceTreeNodeResp n = new RegionDeviceTreeNodeResp();
        n.setNodeType(NODE_REGION);
        n.setId(r.getId());
        n.setName(r.getName());
        n.setCode(r.getCode());
        n.setSort(r.getSort());
        n.setParentId(r.getParentId());
        n.setLocation(null);
        n.setDeviceId(null);
        n.setDeviceType(null);
        n.setIpAddress(null);
        return n;
    }

    private RegionDeviceTreeNodeResp toDeviceNode(UnifiedDeviceInfoTreeRow row, int order) {
        Long regionId = row.getRegionId();
        RegionDeviceTreeNodeResp n = new RegionDeviceTreeNodeResp();
        n.setNodeType(NODE_DEVICE);
        n.setId(row.getId());
        n.setName(row.getDeviceName());
        n.setCode(row.getDeviceId());
        n.setSort(order);
        n.setParentId(null);
        n.setLocation(regionId);
        n.setDeviceId(row.getDeviceId());
        n.setDeviceType(row.getDeviceType());
        n.setIpAddress(row.getIpAddress());
        return n;
    }
}
