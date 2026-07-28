package com.zhiyi.service;

import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.domain.entity.SysUser;
import com.zhiyi.domain.vo.UserProfileBrief;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户展示信息解析：按 creatorId 批量补全昵称与头像，供知识列表/详情使用
 */
@Service
public class UserProfileService {

    private final SysUserMapper sysUserMapper;

    public UserProfileService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 批量解析用户展示信息，避免列表页 N+1 查询
     */
    public Map<Long, UserProfileBrief> batchResolve(Collection<Long> userIdCollection) {
        if (userIdCollection == null || userIdCollection.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> userEntityList = sysUserMapper.selectBatchIds(userIdCollection);
        if (userEntityList == null || userEntityList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserProfileBrief> profileMap = new HashMap<Long, UserProfileBrief>();
        for (SysUser userEntity : userEntityList) {
            if (userEntity == null || userEntity.getId() == null) {
                continue;
            }
            profileMap.put(userEntity.getId(), toProfileBrief(userEntity));
        }
        return profileMap;
    }

    /**
     * 解析单个用户的展示信息
     */
    public UserProfileBrief resolveOne(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser userEntity = sysUserMapper.selectById(userId);
        if (userEntity == null) {
            return null;
        }
        return toProfileBrief(userEntity);
    }

    /**
     * 将 sys_user 转为前端展示用的提交人摘要
     */
    private UserProfileBrief toProfileBrief(SysUser userEntity) {
        UserProfileBrief profileBrief = new UserProfileBrief();
        profileBrief.setUserId(userEntity.getId());
        profileBrief.setNickname(StringUtils.defaultIfBlank(userEntity.getNickname(), userEntity.getUsername()));
        profileBrief.setAvatar(StringUtils.trimToEmpty(userEntity.getAvatarUrl()));
        return profileBrief;
    }
}
