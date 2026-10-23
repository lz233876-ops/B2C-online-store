package com.shopping.common.util;

/**
 * 敏感信息脱敏。
 * <p>
 * 为什么必须做：日志、接口响应、错误信息里一旦出现完整手机号或邮箱，
 * 就等于把用户隐私散播到了所有能看到日志的地方。<b>日志文件的访问权限
 * 通常远低于数据库</b>，这是很容易被忽视的数据泄漏面。
 * <p>
 * 原则：<b>脱敏发生在最外层（VO 组装 / 日志记录时）</b>，
 * 而不是在内层。内层代码拿到真实值才能正常做业务（发短信、比对），
 * 过早脱敏会让业务代码拿到假数据。
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    /**
     * 手机号脱敏：{@code 13812345678 -> 138****5678}。
     * <p>
     * 保留前 3 后 4 是行业惯例：既能让用户确认「是我的号码」，
     * 又不泄漏完整号码。
     */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：{@code zhangsan@example.com -> z******n@example.com}。
     * 保留首尾字符与完整域名，域名本身不敏感。
     */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "******" + local.charAt(local.length() - 1) + domain;
    }

    /**
     * 通用脱敏：只保留首尾各 {@code keep} 个字符，中间用 * 填充。
     * 用于订单号、流水号等在日志里只需定位不需要完整展示的值。
     */
    public static String keepEnds(String value, int keep) {
        if (value == null || value.length() <= keep * 2) {
            return value;
        }
        return value.substring(0, keep)
                + "*".repeat(value.length() - keep * 2)
                + value.substring(value.length() - keep);
    }
}
