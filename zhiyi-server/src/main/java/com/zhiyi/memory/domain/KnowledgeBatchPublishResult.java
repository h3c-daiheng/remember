package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeBatchPublishResult {
    private int total;
    private int published;
    private int failed;
    private List<Failure> failures = new ArrayList<>();

    @Data
    public static class Failure {
        private Long id;
        private String reason;

        public Failure(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }
}
