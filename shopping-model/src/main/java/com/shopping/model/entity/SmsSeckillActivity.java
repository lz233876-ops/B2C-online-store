package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 秒杀活动（{@code sms_seckill_activity}）。
 * <p>
 * 一个活动下挂多个秒杀商品（{@code sms_seckill_sku}），活动有明确的时间窗口。
 * <p>
 * <b>活动状态字段（{@code status}）与「时间窗口」是两套信息，不要混用：</b>
 * <ul>
 *   <li>{@code status} 是<b>运营态</b>：未开始 / 进行中 / 已结束 / 已下线；</li>
 *   <li>{@code start_time ~ end_time} 是<b>时间态</b>：决定「现在能不能买」。</li>
 * </ul>
 * 秒杀接口在热路径上判断的是<b>时间窗口</b>（不查库、走本地缓存），
 * 而运营态用于后台展示与预热任务的触发条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sms_seckill_activity")
public class SmsSeckillActivity extends BaseEntity {

    private Long merchantId;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0 未开始，1 进行中，2 已结束，3 已下线 */
    private Integer status;
    private String description;
    private String banner;
    private Integer sort;
}
