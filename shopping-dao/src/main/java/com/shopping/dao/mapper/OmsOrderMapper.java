package com.shopping.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shopping.model.entity.OmsOrder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    /**
     * <b>订单状态流转（条件更新）</b>——只有「当前状态 == 期望旧状态」才允许改。
     * <p>受影响行数为 0 表示状态已被别人改过，调用方<b>必须</b>抛业务异常。
     *
     * @param fromStatus 期望的旧状态（并发保护的关键，不能省）
     * @return 受影响行数：1 成功，0 状态已变
     */
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("fromStatus") int fromStatus,
                     @Param("toStatus") int toStatus,
                     @Param("payTime") LocalDateTime payTime,
                     @Param("deliveryTime") LocalDateTime deliveryTime,
                     @Param("receiveTime") LocalDateTime receiveTime,
                     @Param("finishTime") LocalDateTime finishTime,
                     @Param("closeTime") LocalDateTime closeTime,
                     @Param("cancelReason") String cancelReason,
                     @Param("payType") Integer payType,
                     @Param("paidAmount") Long paidAmount,
                     @Param("deliveryCompany") String deliveryCompany,
                     @Param("deliveryNo") String deliveryNo);
}
