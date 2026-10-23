package com.shopping.service.pay;

import com.shopping.service.pay.config.PayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * 模拟支付渠道网关。
 * <p>
 * <b>它存在的意义不是「假装支付成功」，而是把真实渠道的「麻烦之处」如实复现出来：</b>
 * 异步通知、签名、重复投递、乱序、金额篡改。
 * 只做「点一下就成功」的假支付，面试时是讲不出东西的。
 * <p>
 * <b>签名规则（刻意对标支付宝）：</b>
 * <ol>
 *   <li>剔除 {@code sign} 与 {@code sign_type} 两个字段；</li>
 *   <li>其余非空参数按 <b>key 的字典序</b>排列；</li>
 *   <li>拼成 {@code k1=v1&k2=v2&...}；</li>
 *   <li>用共享密钥做 <b>HMAC-SHA256</b>，取小写十六进制。</li>
 * </ol>
 * 注意第 2 步：<b>必须排序</b>。HashMap 的遍历顺序不稳定，
 * 不排序会导致「同样的参数算出不同的签名」——这种 bug 在本地单机测试时
 * 因为顺序恰好一致往往不复现，上线才炸。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPayGateway {

    /** 签名时剔除的字段（它们本身不是业务参数） */
    private static final String FIELD_SIGN = "sign";
    private static final String FIELD_SIGN_TYPE = "sign_type";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PayProperties payProperties;

    /**
     * 计算签名。
     *
     * @param params 业务参数（可含 sign，会被自动剔除）
     */
    public String sign(Map<String, String> params) {
        String content = buildSignContent(params);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    payProperties.getSignSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("计算支付签名失败", e);
        }
    }

    /**
     * 验签。
     * <p>⚠️ 用 {@link MessageDigest#isEqual} 做<b>定长比较</b>，而不是 {@code equals}。
     * 字符串 {@code equals} 会在第一个不同字符处返回，攻击者可以据此逐字节
     * 猜出正确签名（时序攻击）。签名比较必须定长。
     */
    public boolean verify(Map<String, String> params, String sign) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        String expected = sign(params);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 组装待签名字符串。
     * <p>用 {@link TreeMap} 保证 key 字典序；空值参数<b>整体剔除</b>
     * （参与签名会让「有值」和「无值」算出的签名不同，而渠道可能省略空字段）。
     */
    private String buildSignContent(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (FIELD_SIGN.equals(key) || FIELD_SIGN_TYPE.equals(key)) {
                continue;
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            sorted.put(key, value);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
