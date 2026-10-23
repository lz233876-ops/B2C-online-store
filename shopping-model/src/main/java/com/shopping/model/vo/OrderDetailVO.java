package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单详情 = 订单主体 + 明细 + 状态流转记录")
public class OrderDetailVO extends OrderVO {

    @Schema(description = "订单明细")
    private List<OrderItemVO> items = new ArrayList<>();

    @Schema(description = "状态流转记录（倒序，最新的在前）")
    private List<OrderHistoryVO> histories = new ArrayList<>();

    @Data
    @Schema(description = "订单状态流转记录")
    public static class OrderHistoryVO {

        @Schema(description = "动作：CREATE/PAY/DELIVER/RECEIVE/CANCEL/TIMEOUT_CLOSE")
        private String action;

        @Schema(description = "变更前状态")
        private Integer fromStatus;

        @Schema(description = "变更后状态")
        private Integer toStatus;

        @Schema(description = "操作人类型：1用户 2管理员 3系统")
        private Integer operatorType;

        @Schema(description = "操作人名称")
        private String operatorName;

        @Schema(description = "备注")
        private String note;

        @Schema(description = "发生时间")
        private java.time.LocalDateTime createTime;
    }
}
