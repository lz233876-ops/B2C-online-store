package com.shopping.service.pay.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付相关配置。
 * <p>
 * {@code signSecret} 是<b>与「渠道」共享的密钥</b>，用于回调验签。
 * 它和 JWT 密钥一样属于机密，只能放在 gitignore 掉的环境配置文件里。
 */
@Data
@Component
@ConfigurationProperties(prefix = "shopping.pay")
public class PayProperties {

    /** 回调验签密钥。绝不能提交到 Git */
    private String signSecret;

    /** 本服务对外的回调地址（真实接入时要在渠道后台配置它） */
    private String notifyUrl;

    /** 支付单有效期（分钟），应与订单的自动关闭时间一致 */
    private int expireMinutes = 30;

    /**
     * 启动即校验密钥 <b>fail fast</b>。
     * <p>如果留到第一次回调才发现没配，那时报的会是「验签失败」——
     * 排查方向会完全跑偏到「渠道配置错了」，而实际原因只是本地少配了一个环境变量。
     */
    @PostConstruct
    void validate() {
        if (signSecret == null || signSecret.isBlank()) {
            throw new IllegalStateException(
                    "未配置支付验签密钥。请在 application-dev.yml 里设置 shopping.pay.sign-secret。"
                            + "该值绝不能提交到 Git。");
        }
        if (signSecret.length() < 16) {
            throw new IllegalStateException(
                    "支付验签密钥太短（当前 " + signSecret.length() + " 字符），至少 16 字符。");
        }
    }
}
