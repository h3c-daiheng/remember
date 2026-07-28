package com.zhiyi.memory.governance;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * 工作空间治理配置：解析 workspace.governance_config_json 并与全局默认值合并
 */
public final class GovernanceWorkspaceConfig {

    private final boolean autoResolveEnabled;

    private final double autoResolveSimilarityThreshold;

    private GovernanceWorkspaceConfig(boolean autoResolveEnabled, double autoResolveSimilarityThreshold) {
        this.autoResolveEnabled = autoResolveEnabled;
        this.autoResolveSimilarityThreshold = autoResolveSimilarityThreshold;
    }

    /**
     * 合并全局配置与工作空间覆盖项
     */
    public static GovernanceWorkspaceConfig merge(String governanceConfigJson,
                                                    boolean globalAutoResolveEnabled,
                                                    double globalAutoResolveThreshold) {
        if (StringUtils.isBlank(governanceConfigJson)) {
            return new GovernanceWorkspaceConfig(globalAutoResolveEnabled, globalAutoResolveThreshold);
        }
        try {
            JSONObject configObject = JSONUtil.parseObj(governanceConfigJson);
            boolean autoResolveEnabled = configObject.containsKey("autoResolveEnabled")
                    ? configObject.getBool("autoResolveEnabled")
                    : globalAutoResolveEnabled;
            double autoResolveThreshold = configObject.containsKey("autoResolveSimilarityThreshold")
                    ? configObject.getDouble("autoResolveSimilarityThreshold")
                    : globalAutoResolveThreshold;
            return new GovernanceWorkspaceConfig(autoResolveEnabled, autoResolveThreshold);
        } catch (Exception exception) {
            return new GovernanceWorkspaceConfig(globalAutoResolveEnabled, globalAutoResolveThreshold);
        }
    }

    public boolean isAutoResolveEnabled() {
        return autoResolveEnabled;
    }

    public double getAutoResolveSimilarityThreshold() {
        return autoResolveSimilarityThreshold;
    }

    /**
     * 构建可持久化到 workspace 表的 JSON
     */
    public static String toJson(boolean autoResolveEnabled, double autoResolveSimilarityThreshold) {
        JSONObject configObject = new JSONObject();
        configObject.set("autoResolveEnabled", autoResolveEnabled);
        configObject.set("autoResolveSimilarityThreshold", autoResolveSimilarityThreshold);
        return configObject.toString();
    }
}
