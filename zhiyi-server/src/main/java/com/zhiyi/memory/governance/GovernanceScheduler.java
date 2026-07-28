package com.zhiyi.memory.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.config.GovernanceProperties;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.memory.domain.GovernanceScanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 治理定时任务：按 Cron 对各工作空间执行增量扫描与质量校验
 */
@Component
public class GovernanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GovernanceScheduler.class);

    private final GovernanceProperties governanceProperties;
    private final WorkspaceMapper workspaceMapper;
    private final GovernanceService governanceService;

    public GovernanceScheduler(GovernanceProperties governanceProperties,
                               WorkspaceMapper workspaceMapper,
                               GovernanceService governanceService) {
        this.governanceProperties = governanceProperties;
        this.workspaceMapper = workspaceMapper;
        this.governanceService = governanceService;
    }

    /**
     * 定时增量治理：重复扫描 + 质量校验
     */
    @Scheduled(cron = "${zhiyi.governance.incremental-scan-cron:0 0 3 * * ?}")
    public void runScheduledGovernance() {
        if (!governanceProperties.isSchedulerEnabled()) {
            return;
        }

        LambdaQueryWrapper<WorkspaceEntity> queryWrapper = new LambdaQueryWrapper<WorkspaceEntity>();
        queryWrapper.eq(WorkspaceEntity::getStatus, 1);
        List<WorkspaceEntity> workspaceEntityList = workspaceMapper.selectList(queryWrapper);
        log.info("治理定时任务开始 workspaceCount={}", workspaceEntityList.size());

        for (WorkspaceEntity workspaceEntity : workspaceEntityList) {
            if (workspaceEntity == null || workspaceEntity.getId() == null) {
                continue;
            }
            try {
                // 须经 Spring 代理调用，确保各扫描方法的 @Transactional 生效
                runWorkspaceGovernanceScans(workspaceEntity.getId());
            } catch (Exception exception) {
                log.warn("治理定时任务失败 workspaceId={} reason={}",
                        workspaceEntity.getId(), exception.getMessage());
            }
        }
        log.info("治理定时任务结束");
    }

    /**
     * 对工作空间执行增量重复扫描与质量校验（各扫描独立事务）
     */
    private void runWorkspaceGovernanceScans(String workspaceId) {
        if (governanceProperties.isIncrementalScanEnabled()) {
            GovernanceScanRequest incrementalRequest = new GovernanceScanRequest();
            incrementalRequest.setScanType(GovernanceConstants.SCAN_TYPE_INCREMENTAL);
            incrementalRequest.setScanMode(GovernanceConstants.SCAN_MODE_DUPLICATE);
            governanceService.runDuplicateScan(incrementalRequest, workspaceId, null);
        }
        if (governanceProperties.isValidateScanEnabled()) {
            GovernanceScanRequest validateRequest = new GovernanceScanRequest();
            validateRequest.setScanType(GovernanceConstants.SCAN_TYPE_VALIDATE);
            validateRequest.setScanMode(GovernanceConstants.SCAN_MODE_VALIDATE);
            governanceService.runValidateScan(validateRequest, workspaceId, null);
        }
    }
}
