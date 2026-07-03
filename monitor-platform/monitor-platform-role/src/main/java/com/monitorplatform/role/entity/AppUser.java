package com.monitorplatform.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("app_user")
public class AppUser {

    //  用户id
    @TableId(type = IdType.AUTO)
    private Long id;

    //  用户名称
    private String username;

    //  用户密码
    private String password;

    //  关联角色id
    private Long roleId;

    //  关联角色名称
    private String roleName;

    //  用户描述
    private String description;

    //  用户创建时间
    private Date createTime;

    //  用户更新时间
    private Date updateTime;

    //  用户关联ukey
    private String ukeyId;

    //  工号
    private String employeeNo;

    //  岗位
    private String post;

    //  年龄
    private Integer age;

    //  性别
    private String gender;

    //  头像
    private String avatarUrl;

    //  手机号
    private String phone;

    /** 1=可修改, 0=不可修改 */
    private Integer isAllowChange;
}
