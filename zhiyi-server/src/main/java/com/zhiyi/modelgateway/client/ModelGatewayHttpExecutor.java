package com.zhiyi.modelgateway.client;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import org.apache.commons.lang3.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * ai-gateway HTTP 调用封装：将网络层异常转为可读的业务异常
 */
final class ModelGatewayHttpExecutor {

    private ModelGatewayHttpExecutor() {
    }

    /**
     * 执行 HTTP 请求，连接失败或超时时抛出 502 业务异常
     *
     * @param httpRequest HTTP 请求
     * @param baseUrl     配置的 ai-gateway 地址，便于排查
     * @param apiLabel    接口说明，例如 Chat / Embedding
     */
    static HttpResponse execute(HttpRequest httpRequest, String baseUrl, String apiLabel) {
        try {
            return httpRequest.execute();
        } catch (IORuntimeException ioRuntimeException) {
            throw buildNetworkException(baseUrl, apiLabel, ioRuntimeException);
        }
    }

    /**
     * 根据底层网络异常类型生成明确提示
     */
    private static BusinessException buildNetworkException(String baseUrl, String apiLabel,
                                                           IORuntimeException ioRuntimeException) {
        Throwable rootCause = resolveRootCause(ioRuntimeException);
        String gatewayAddress = baseUrl == null ? "未配置" : baseUrl;

        if (rootCause instanceof ConnectException) {
            return new BusinessException(502,
                    "ai-gateway " + apiLabel + " 不可达（连接被拒绝），请确认服务已启动且 base-url 正确：" + gatewayAddress);
        }
        if (rootCause instanceof UnknownHostException) {
            return new BusinessException(502,
                    "ai-gateway " + apiLabel + " 地址无法解析，请检查 base-url：" + gatewayAddress);
        }
        if (rootCause instanceof SocketTimeoutException) {
            String timeoutMessage = rootCause.getMessage();
            if (timeoutMessage != null && timeoutMessage.toLowerCase().contains("connect")) {
                return new BusinessException(502,
                        "ai-gateway " + apiLabel + " 连接超时，请检查网络或 base-url：" + gatewayAddress);
            }
            return new BusinessException(502, "ai-gateway " + apiLabel + " 响应超时，请稍后重试");
        }

        String detail = rootCause == null ? ioRuntimeException.getMessage() : rootCause.getMessage();
        return new BusinessException(502,
                "ai-gateway " + apiLabel + " 网络异常（" + detail + "），base-url：" + gatewayAddress);
    }

    /**
     * 获取最底层 cause，便于识别 ConnectException 等具体类型
     */
    private static Throwable resolveRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 解析 ai-gateway HTTP 错误响应，401 等状态给出可操作的提示
     */
    static BusinessException buildHttpStatusException(HttpResponse httpResponse, String apiLabel) {
        int status = httpResponse.getStatus();
        String detail = extractResponseMessage(httpResponse.body());
        if (status == 401) {
            return new BusinessException(502,
                    "ai-gateway " + apiLabel + " 鉴权失败：" + detail + "，请检查 app-id 与 app-token 是否与 ai_app_client 表一致");
        }
        if (status == 403) {
            return new BusinessException(502, "ai-gateway " + apiLabel + " 无权限：" + detail);
        }
        if (status >= 500) {
            return new BusinessException(502, "ai-gateway " + apiLabel + " 服务异常（HTTP " + status + "）：" + detail);
        }
        return new BusinessException(502, "ai-gateway " + apiLabel + " 调用失败（HTTP " + status + "）：" + detail);
    }

    /**
     * 从 ai-gateway JSON 响应中提取 message 字段
     */
    private static String extractResponseMessage(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return "无响应内容";
        }
        try {
            Result<?> result = JSONUtil.toBean(responseBody, Result.class, false);
            if (result != null && StringUtils.isNotBlank(result.getMessage())) {
                return result.getMessage();
            }
        } catch (Exception parseException) {
            // 非标准 JSON 时直接返回原文，便于排查
        }
        return responseBody;
    }
}
