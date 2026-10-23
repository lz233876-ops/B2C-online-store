package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟渠道通知的「回执」。
 * <p>
 * 这个 VO 把网关<b>实际发出去的报文和签名原样返回</b>，用途有二：
 * <ol>
 *   <li>验收脚本可以拿它<b>重放</b>同一条通知，验证幂等；</li>
 *   <li>验收脚本可以<b>篡改</b>它（改金额、改签名）再发一次，验证验签与金额校验真的拦得住。</li>
 * </ol>
 * 也就是说：<b>验签与幂等这两个最容易被「假实现」糊弄过去的点，在这里变成了可被外部攻击测试的对象。</b>
 */
@Data
@Schema(description = "模拟渠道通知回执")
public class MockNotifyVO {

    @Schema(description = "渠道通知ID")
    private String notifyId;

    @Schema(description = "网关发出的通知参数（可直接重放）")
    private Map<String, String> params = new LinkedHashMap<>();

    @Schema(description = "网关计算的签名")
    private String sign;

    @Schema(description = "业务处理是否成功")
    private Boolean success;

    @Schema(description = "是否为重复通知")
    private Boolean duplicate;

    @Schema(description = "是否需要原路退款")
    private Boolean needRefund;

    @Schema(description = "处理说明")
    private String message;

    @Schema(description = "渠道期望的回执（真实渠道要求返回 success 才停止重试）")
    private String notifyResponse;
}
