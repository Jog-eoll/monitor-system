package com.monitorplatform.role.mapper;

import com.monitorplatform.role.entity.Permission;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface PermissionMapper {

    @Insert("INSERT INTO permission(type, name, code, route_url, plugin_url, icon_url, sort, is_enable, parent_id) " +
            "VALUES(#{type}, #{name}, #{code}, #{routeUrl}, #{pluginUrl}, #{iconUrl}, #{sort}, #{isEnable}, #{parentId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Permission permission);

    @Select("SELECT id, type, name, code, route_url, plugin_url, icon_url, sort, is_enable, parent_id " +
            "FROM permission WHERE id = #{id}")
    Permission selectById(@Param("id") Long id);

    @Select("SELECT id, type, name, code, route_url, plugin_url, icon_url, sort, is_enable, parent_id " +
            "FROM permission WHERE code = #{code}")
    Permission selectByCode(@Param("code") String code);

    @Update("UPDATE permission SET type = #{type}, name = #{name}, code = #{code}, route_url = #{routeUrl}, " +
            "plugin_url = #{pluginUrl}, icon_url = #{iconUrl}, sort = #{sort}, is_enable = #{isEnable}, parent_id = #{parentId} " +
            "WHERE id = #{id}")
    int updateById(Permission permission);

    @Delete("DELETE FROM permission WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM permission WHERE parent_id = #{parentId}")
    long countByParentId(@Param("parentId") Long parentId);

    @Select("SELECT COALESCE(MAX(sort), 0) FROM permission WHERE parent_id = #{parentId}")
    Integer selectMaxSortByParentId(@Param("parentId") Long parentId);

    @Select("SELECT id, type, name, code, route_url, plugin_url, icon_url, sort, is_enable, parent_id " +
            "FROM permission ORDER BY parent_id ASC, sort ASC, id ASC")
    List<Permission> selectAllOrderByTree();

    @Select("SELECT id, type, name, code, route_url, plugin_url, icon_url, sort, is_enable, parent_id " +
            "FROM permission WHERE parent_id = #{parentId} ORDER BY sort ASC, id ASC")
    List<Permission> selectByParentId(@Param("parentId") Long parentId);

    @Update("UPDATE permission SET sort = sort + 1 WHERE parent_id = #{parentId} AND sort >= #{fromSort}")
    int increaseSortFrom(@Param("parentId") Long parentId, @Param("fromSort") Integer fromSort);

    @Update("UPDATE permission SET sort = sort - 1 WHERE parent_id = #{parentId} AND sort > #{fromSort}")
    int decreaseSortAfter(@Param("parentId") Long parentId, @Param("fromSort") Integer fromSort);

    @Update("UPDATE permission SET sort = sort - 1 WHERE parent_id = #{parentId} AND sort > #{oldSort} AND sort <= #{newSort}")
    int decreaseSortRange(@Param("parentId") Long parentId, @Param("oldSort") Integer oldSort, @Param("newSort") Integer newSort);

    @Update("UPDATE permission SET sort = sort + 1 WHERE parent_id = #{parentId} AND sort >= #{newSort} AND sort < #{oldSort}")
    int increaseSortRange(@Param("parentId") Long parentId, @Param("oldSort") Integer oldSort, @Param("newSort") Integer newSort);

    @Select("SELECT p.id, p.type, p.name, p.code, p.route_url AS routeUrl, p.plugin_url AS pluginUrl, p.icon_url AS iconUrl, p.sort, p.is_enable AS isEnable, p.parent_id AS parentId " +
            "FROM permission p JOIN role r ON r.id = #{roleId} " +
            "WHERE r.permission_codes IS NOT NULL AND r.permission_codes != '' " +
            "AND (TRIM(r.permission_codes) = '*' OR FIND_IN_SET(\n" +
            "  (CAST(p.id AS CHAR) COLLATE utf8mb4_unicode_ci),\n" +
            "  (r.permission_codes COLLATE utf8mb4_unicode_ci)\n" +
            ") > 0) " +
            "ORDER BY p.sort ASC, p.id ASC")
    List<Permission> selectByRoleId(@Param("roleId") Long roleId);
}
