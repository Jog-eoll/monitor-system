package com.monitorplatform.role.mapper;

import com.monitorplatform.role.entity.SystemParameters;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface SystemParametersMapper {

    @Insert("INSERT INTO system_parameters(name, code, parameters, create_time, update_time) " +
            "VALUES(#{name}, #{code}, #{parameters}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemParameters entity);

    @Select("SELECT id, name, code, parameters, create_time AS createTime, update_time AS updateTime " +
            "FROM system_parameters WHERE id = #{id}")
    SystemParameters selectById(@Param("id") Long id);

    @Select("SELECT id, name, code, parameters, create_time AS createTime, update_time AS updateTime " +
            "FROM system_parameters WHERE code = #{code}")
    SystemParameters selectByCode(@Param("code") String code);

    @Update("UPDATE system_parameters SET name = #{name}, code = #{code}, parameters = #{parameters}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateById(SystemParameters entity);

    @Delete("DELETE FROM system_parameters WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, name, code, parameters, create_time AS createTime, update_time AS updateTime " +
            "FROM system_parameters WHERE 1=1 " +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%')</if> " +
            "<if test='code != null and code != \"\"'> AND code LIKE CONCAT('%', #{code}, '%')</if> " +
            "ORDER BY update_time DESC LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<SystemParameters> selectPage(@Param("name") String name,
                                      @Param("code") String code,
                                      @Param("offset") Integer offset,
                                      @Param("pageSize") Integer pageSize);

    @Select("<script>" +
            "SELECT COUNT(1) FROM system_parameters WHERE 1=1 " +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%')</if> " +
            "<if test='code != null and code != \"\"'> AND code LIKE CONCAT('%', #{code}, '%')</if> " +
            "</script>")
    long count(@Param("name") String name, @Param("code") String code);
}
