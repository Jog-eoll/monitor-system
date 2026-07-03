package com.monitorplatform.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("role")
public class Role {

    //  角色id
    @TableId(type = IdType.AUTO)
    private Long id;

    //  角色名称
    private String name;

    //  角色编码
    private String code;

    //  角色状态：DISABLED-禁用 ENABLED-启用
    private String status;

    //  创建时间
    private Date createTime;

    //  更新时间
    private Date updateTime;

    //  角色描述
    private String description;

    //  角色权限
    private String permissionCodes;

    //  getter/setter

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(String permissionCodes) {
        this.permissionCodes = permissionCodes;
    }
}
