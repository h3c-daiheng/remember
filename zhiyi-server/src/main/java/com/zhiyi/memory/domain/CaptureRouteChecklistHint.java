package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Capture 归类 AI 对 Review 检查项的预审提示，供 Review 人复核
 */
@Data
public class CaptureRouteChecklistHint {

    /** 检查项编号，如 R1、Q2、C3 */
    private String id;

    /** 预审结论：pass / warn / fail */
    private String status;

    /** 判定依据摘要 */
    private String evidence;

    /** 证据片段，供前端在 Fact 编辑器中高亮 */
    private List<CaptureChecklistEvidenceSpan> evidenceSpans = new ArrayList<CaptureChecklistEvidenceSpan>();
}
