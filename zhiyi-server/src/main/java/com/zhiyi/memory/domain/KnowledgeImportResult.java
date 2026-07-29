package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入经验结果
 */
@Data
public class KnowledgeImportResult {
    private int total;
    private int imported;
    private int failed;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private int index;
        private String title;
        private String reason;

        public Failure(int index, String title, String reason) {
            this.index = index;
            this.title = title;
            this.reason = reason;
        }
    }
}
