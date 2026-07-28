package com.zhiyi.memory.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Capture AI Review 异步触发服务：事务提交后后台执行，不阻塞 Submit
 */
@Service
public class AiReviewAsyncService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewAsyncService.class);

    private final AiReviewEngine aiReviewEngine;

    public AiReviewAsyncService(AiReviewEngine aiReviewEngine) {
        this.aiReviewEngine = aiReviewEngine;
    }

    /**
     * 异步审核指定 Capture 草稿（Agent Submit 等无登录态场景）
     */
    @Async("aiReviewExecutor")
    public void reviewDraftAsync(Long draftId) {
        reviewDraftAsync(draftId, null);
    }

    /**
     * 异步审核指定 Capture 草稿
     *
     * @param reviewerId Web 重跑等有人工触发者时传入，供自动采纳/路由写入审核人与 creator_id
     */
    @Async("aiReviewExecutor")
    public void reviewDraftAsync(Long draftId, Long reviewerId) {
        try {
            aiReviewEngine.reviewDraft(draftId, reviewerId);
        } catch (Exception exception) {
            log.warn("异步 AI Review 未捕获异常 draftId={} reviewerId={} message={}",
                    draftId, reviewerId, exception.getMessage(), exception);
        }
    }
}
