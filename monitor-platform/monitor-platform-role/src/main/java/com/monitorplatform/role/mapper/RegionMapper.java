package com.monitorplatform.role.mapper;

import com.monitorplatform.role.entity.Region;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface RegionMapper {

    @Insert("INSERT INTO region(name, parent_id, code, sort) VALUES(#{name}, #{parentId}, #{code}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Region region);

    @Select("SELECT id, name, code, sort, parent_id AS parentId FROM region WHERE id = #{id}")
    Region selectById(@Param("id") Long id);

    @Select("SELECT id, name, code, sort, parent_id AS parentId FROM region WHERE code = #{code}")
    Region selectByCode(@Param("code") String code);

    @Update("UPDATE region SET name = #{name}, code = #{code}, sort = #{sort}, parent_id = #{parentId} WHERE id = #{id}")
    int updateById(Region region);

    @Delete("DELETE FROM region WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COALESCE(MAX(sort), 0) FROM region WHERE parent_id = #{parentId}")
    Integer selectMaxSortByParentId(@Param("parentId") Long parentId);

    @Select("SELECT id, name, code, sort, parent_id AS parentId FROM region WHERE parent_id = #{parentId} ORDER BY sort ASC, id ASC")
    List<Region> selectByParentId(@Param("parentId") Long parentId);

    @Select("SELECT id, name, code, sort, parent_id AS parentId FROM region ORDER BY parent_id ASC, sort ASC, id ASC")
    List<Region> selectAllOrderByTree();

    @Select("<script>" +
            "SELECT id, name, code, sort, parent_id AS parentId FROM region WHERE 1=1 " +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%')</if> " +
            "<if test='code != null and code != \"\"'> AND code LIKE CONCAT('%', #{code}, '%')</if> " +
            "ORDER BY parent_id ASC, sort ASC, id ASC LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<Region> selectPage(@Param("name") String name,
                            @Param("code") String code,
                            @Param("offset") Integer offset,
                            @Param("pageSize") Integer pageSize);

    @Select("<script>" +
            "SELECT COUNT(1) FROM region WHERE 1=1 " +
            "<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%', #{name}, '%')</if> " +
            "<if test='code != null and code != \"\"'> AND code LIKE CONCAT('%', #{code}, '%')</if> " +
            "</script>")
    long count(@Param("name") String name, @Param("code") String code);

    @Delete("<script>" +
            "DELETE FROM region WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteByIds(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM region")
    int deleteAll();

    /** 指定主键插入（用于前端带回 id 的全量覆盖） */
    @Insert("INSERT INTO region(id, name, parent_id, code, sort) VALUES(#{id}, #{name}, #{parentId}, #{code}, #{sort})")
    int insertWithId(Region region);
}
