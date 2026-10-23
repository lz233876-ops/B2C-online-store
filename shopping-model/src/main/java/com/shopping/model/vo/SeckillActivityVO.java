package com.shopping.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "秒杀活动（含秒杀商品）")
public class SeckillActivityVO {

    @Schema(description = "活动ID")
    private Long id;

    @Schema(description = "活动名称")
    private String activityName;

    @Schema(description = "活动说明")
    private String description;

    @Schema(description = "Banner 图")
    private String banner;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "状态：0未开始 1进行中 2已结束 3已下线")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "距开始/结束的秒数（进行中则是距结束）")
    private Long remainSeconds;

    @Schema(description = "秒杀商品列表")
    private List<SeckillSkuVO> skus = new ArrayList<>();
}
