package com.monitorplatform.role.mapper;

import com.monitorplatform.role.entity.Role;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface RoleMapper {

    @Insert("INSERT INTO role(code, name, description, status, permission_codes, create_time, update_time) " +
            "VALUES(#{code}, #{name}, #{description}, #{status}, #{permissionCodes}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Role role);

    @Select("SELECT id, code, name, description, status, permission_codes AS permissionCodes, create_time AS createTime, update_time AS updateTime " +
            "FROM role WHERE id = #{id}")
    Role selectById(@Param("id") Long id);

    @Select("SELECT id, code, name, description, status, permission_codes AS permissionCodes, create_time AS createTime, update_time AS updateTime " +
            "FROM role WHERE code = #{code}")
    Role selectByCode(@Param("code") String code);

    @Update("UPDATE role SET name = #{name}, description = #{description}, status = #{status}, permission_codes = #{permissionCodes}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateById(Role role);

    @Update("UPDATE role SET permission_codes = #{permissionCodes}, update_time = NOW() WHERE id = #{roleId}")
    int updatePermissionCodesById(@Param("roleId") Long roleId, @Param("permissionCodes") String permissionCodes);

    @Delete("DELETE FROM role WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, code, name, description, status, permission_codes AS permissionCodes, create_time AS createTime, update_time AS updateTime " +
            "FROM role WHERE 1=1 " +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%')</if> " +
            "<if test='code != null and code != \"\"'> AND code LIKE CONCAT('%', #{code}, '%')</if> " +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if> " +
            "ORDER BY update_time DESC LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<Role> selectPage(@Param("name") String name,
                          @Param("code") String code,
                          @Param("status") String status,
                          @Param("offset") Integer offset,
                          @Param("pageSize") Integer pageSize);

    @Select("<script>" +
            "SELECT COUNT(1) FROM role WHERE 1=1 " +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%')</if> " +
            "<if test='code != null and code != \"\"'> AND code LIKE CONCAT('%', #{code}, '%')</if> " +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if> " +
            "</script>")
    long count(@Param("name") String name,
               @Param("code") String code,
               @Param("status") String status);

    @Select("SELECT CASE WHEN permission_codes IS NULL OR permission_codes = '' THEN 0 " +
            "WHEN TRIM(permission_codes) = '*' THEN (SELECT COUNT(1) FROM permission) " +
            "ELSE LENGTH(permission_codes) - LENGTH(REPLACE(permission_codes, ',', '')) + 1 END " +
            "FROM role WHERE id = #{roleId}")
    int countPermissionsByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(1) FROM app_user WHERE role_id = #{roleId}")
    int countUsersByRoleId(@Param("roleId") Long roleId);
}
