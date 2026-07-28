package com.zhiyi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.UsageDailyMapper;
import com.zhiyi.domain.entity.UsageDailyEntity;
import com.zhiyi.memory.MemoryConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 日用量统计服务：Memory API 成功后异步累加 usage_daily
 */
@Service
public class UsageDailyService {

    private static final Logger log = LoggerFactory.getLogger(UsageDailyService.class);

    /** 商业化恢复前不启用 Recall 配额硬限流 */
    private static final boolean QUOTA_ENFORCEMENT_ENABLED = false;

    private final UsageDailyMapper usageDailyMapper;

    public UsageDailyService(UsageDailyMapper usageDailyMapper) {
        this.usageDailyMapper = usageDailyMapper;
    }

    /**
     * 异步累加当日用量，供 MemoryOperationLogService 在成功落库后调用
     */
    @Async("usageDailyExecutor")
    public void incrementAsync(String workspaceId, String operation, long embeddingTokens) {
        incrementQuietly(workspaceId, operation, embeddingTokens);
    }

    /**
     * 查询指定工作空间在某自然月的 Recall 累计次数，供后续配额校验
     */
    public int getMonthlyRecallUsed(String workspaceId, YearMonth yearMonth) {
        if (StringUtils.isBlank(workspaceId) || yearMonth == null) {
            return 0;
        }
        Date startDate = Date.valueOf(yearMonth.atDay(1));
        Date endDate = Date.valueOf(yearMonth.atEndOfMonth());

        LambdaQueryWrapper<UsageDailyEntity> queryWrapper = new LambdaQueryWrapper<UsageDailyEntity>();
        queryWrapper.eq(UsageDailyEntity::getWorkspaceId, workspaceId)
                .between(UsageDailyEntity::getUsageDate, startDate, endDate);

        List<UsageDailyEntity> entityList = usageDailyMapper.selectList(queryWrapper);
        int totalRecall = 0;
        for (UsageDailyEntity entity : entityList) {
            totalRecall += safeInt(entity.getRecallCount());
        }
        return totalRecall;
    }

    /**
     * 校验 Recall 配额（商业化恢复后将 QUOTA_ENFORCEMENT_ENABLED 设为 true 并在 Memory API 入口调用）
     */
    public void assertRecallQuotaAvailable(String workspaceId, int recallQuota) {
        if (!QUOTA_ENFORCEMENT_ENABLED || StringUtils.isBlank(workspaceId)) {
            return;
        }
        int recallUsed = getMonthlyRecallUsed(workspaceId, YearMonth.now());
        if (recallQuota > 0 && recallUsed >= recallQuota) {
            throw new BusinessException(429, "本月 Recall 配额已用尽");
        }
    }

    /**
     * 按操作类型累加当日用量，失败仅打日志不影响主流程
     */
    private void incrementQuietly(String workspaceId, String operation, long embeddingTokens) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(operation)) {
            return;
        }
        int recallIncrement = 0;
        int rememberIncrement = 0;
        int feedbackIncrement = 0;
        if (MemoryConstants.OPERATION_RECALL.equals(operation)
                || MemoryConstants.OPERATION_SEARCH.equals(operation)) {
            recallIncrement = 1;
        } else if (MemoryConstants.OPERATION_REMEMBER.equals(operation)
                || MemoryConstants.OPERATION_SUBMIT.equals(operation)) {
            rememberIncrement = 1;
        } else if (MemoryConstants.OPERATION_FEEDBACK.equals(operation)) {
            feedbackIncrement = 1;
        } else {
            return;
        }
        try {
            Date usageDate = Date.valueOf(LocalDate.now());
            long safeEmbeddingTokens = Math.max(embeddingTokens, 0L);
            usageDailyMapper.upsertIncrement(workspaceId, usageDate,
                    recallIncrement, rememberIncrement, feedbackIncrement, safeEmbeddingTokens);
        } catch (Exception exception) {
            log.warn("usage_daily 累加失败 workspaceId={} operation={}", workspaceId, operation, exception);
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
