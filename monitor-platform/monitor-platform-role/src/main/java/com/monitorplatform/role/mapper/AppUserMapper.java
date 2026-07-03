package com.monitorplatform.role.mapper;

import com.monitorplatform.role.entity.AppUser;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface AppUserMapper {
    @Insert("INSERT INTO app_user(username, password, role_id, role_name, description, create_time, update_time, ukey_id, " +
            "employee_no, post, age, gender, avatar_url, phone, is_allow_change) " +
            "VALUES(#{username}, #{password}, #{roleId}, #{roleName}, #{description}, NOW(), NOW(), #{ukeyId}, " +
            "#{employeeNo}, #{post}, #{age}, #{gender}, #{avatarUrl}, #{phone}, #{isAllowChange})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AppUser user);

    @Select("SELECT id, username, password, role_id AS roleId, role_name AS roleName, description, create_time AS createTime, update_time AS updateTime, ukey_id AS ukeyId, employee_no AS employeeNo," +
            "post," +
            "age," +
            "gender," +
            "avatar_url AS avatarUrl," +
            "phone," +
            "is_allow_change AS isAllowChange " +
            "FROM app_user WHERE id = #{id}")
    AppUser selectById(@Param("id") Long id);

    @Select("SELECT id, username, password, role_id AS roleId, role_name AS roleName, description, create_time AS createTime, update_time AS updateTime, ukey_id AS ukeyId, employee_no AS employeeNo," +
            "post," +
            "age," +
            "gender," +
            "avatar_url AS avatarUrl," +
            "phone," +
            "is_allow_change AS isAllowChange " +
            "FROM app_user WHERE username = #{username}")
    AppUser selectByUsername(@Param("username") String username);

    @Select("SELECT id, username, password, role_id AS roleId, role_name AS roleName, description, create_time AS createTime, update_time AS updateTime, ukey_id AS ukeyId, employee_no AS employeeNo," +
            "post," +
            "age," +
            "gender," +
            "avatar_url AS avatarUrl," +
            "phone," +
            "is_allow_change AS isAllowChange " +
            "FROM app_user WHERE ukey_id = #{ukeyId}")
    AppUser selectByUkeyId(@Param("ukeyId") String ukeyId);

    @Update("UPDATE app_user SET username = #{username}, password = #{password}, role_id = #{roleId}, role_name = #{roleName}, " +
            "description = #{description}, ukey_id = #{ukeyId}, " +
            "employee_no = #{employeeNo}, post = #{post}, age = #{age}, gender = #{gender}, " +
            "avatar_url = #{avatarUrl}, phone = #{phone}, is_allow_change = #{isAllowChange}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updateById(AppUser user);

    @Update("UPDATE app_user SET role_id = #{roleId}, role_name = #{roleName}, update_time = NOW() WHERE id = #{userId}")
    int updateRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("roleName") String roleName);

    @Delete("DELETE FROM app_user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, username, password, role_id AS roleId, role_name AS roleName, description, create_time AS createTime, update_time AS updateTime, ukey_id AS ukeyId, employee_no AS employeeNo," +
            "post," +
            "age," +
            "gender," +
            "avatar_url AS avatarUrl," +
            "phone," +
            "is_allow_change AS isAllowChange " +
            "FROM app_user WHERE 1=1 " +
            "<if test='username != null and username != \"\"'> AND username LIKE CONCAT('%', #{username}, '%')</if> " +
            "<if test='roleId != null'> AND role_id = #{roleId}</if> " +
            "ORDER BY update_time DESC LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<AppUser> selectPage(@Param("username") String username,
                             @Param("roleId") Long roleId,
                             @Param("offset") Integer offset,
                             @Param("pageSize") Integer pageSize);

    @Select("<script>" +
            "SELECT COUNT(1) FROM app_user WHERE 1=1 " +
            "<if test='username != null and username != \"\"'> AND username LIKE CONCAT('%', #{username}, '%')</if> " +
            "<if test='roleId != null'> AND role_id = #{roleId}</if> " +
            "</script>")
    long count(@Param("username") String username, @Param("roleId") Long roleId);

}