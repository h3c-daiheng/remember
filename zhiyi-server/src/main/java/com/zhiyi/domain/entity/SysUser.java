package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 系统用户实体，对应 sys_user 表
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** gonline 平台用户主键，SSO 登录绑定 */
    private String gonlineUserId;

    /** 登录账号 */
    private String username;

    /** 登录密码哈希 */
    private String password;

    /** 最近使用的工作空间主键 */
    private String lastWorkspaceId;

    /** 用户昵称 */
    private String nickname;

    /** 用户头像地址，SSO 登录时从主站同步 */
    private String avatarUrl;

    /** 状态：1-正常 0-禁用 */
    private Integer status;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
