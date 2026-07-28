package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 用户展示摘要：用于知识卡片、详情等场景展示提交人昵称与头像
 */
@Data
public class UserProfileBrief {

    /** 本地 sys_user.id */
    private Long userId;

    /** 展示昵称，缺省时回退 username */
    private String nickname;

    /** 头像 URL，可为空 */
    private String avatar;
}
