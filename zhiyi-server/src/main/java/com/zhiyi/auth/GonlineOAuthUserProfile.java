package com.zhiyi.auth;

import lombok.Data;

/**
 * gonline OAuth check_token 解析出的用户摘要，用于同步本地 sys_user
 */
@Data
public class GonlineOAuthUserProfile {

    /** gonline 平台用户主键 */
    private String gonlineUserId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;
}
