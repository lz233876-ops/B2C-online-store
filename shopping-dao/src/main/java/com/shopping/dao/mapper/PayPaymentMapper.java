package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.PayPayment;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface PayPaymentMapper extends BaseMapper<PayPayment> {

    /**
     * 把支付单标记为成功 —— <b>业务幂等的唯一落点</b>。
     * <p>条件 {@code status = 0} 保证重复回调只有第一次生效，
     * 后续返回 0 行，调用方据此判定「已处理过」。
     *
     * @return 1 本次真的改成成功；0 说明已被处理（重复回调）
     */
    int markSuccess(@Param("paymentNo") String paymentNo,
                    @Param("tradeNo") String tradeNo,
                    @Param("buyerAccount") String buyerAccount,
                    @Param("paidAmount") Long paidAmount,
                    @Param("payTime") LocalDateTime payTime,
                    @Param("notifyData") String notifyData);

    /** 关闭支付单（只允许从「待支付」出发） */
    int closePayment(@Param("paymentNo") String paymentNo, @Param("errorMsg") String errorMsg);

    /** 标记支付失败（只允许从「待支付」出发） */
    int markFailed(@Param("paymentNo") String paymentNo, @Param("errorMsg") String errorMsg);
}
