package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车项（{@code oms_cart_item}）。
 * <p>
 * <b>本表大量使用「快照」字段</b>（{@code price} / {@code skuName} / {@code skuImage} /
 * {@code specDesc} / {@code spuStatus}）。原因：商品随时可能改名、改价、下架、甚至删除，
 * 而购物车是要长期存在的。如果没有快照，用户第二天打开购物车会看到：
 * <ul>
 *   <li>商品名变成空白（SKU 被删了）；</li>
 *   <li>价格显示成别的数字（SKU 改了价），用户会认为「你偷偷改我购物车里的价格」。</li>
 * </ul>
 * 所以：<b>快照负责「展示稳定」，实时价负责「结算正确」</b>，两者都要有。
 * <p>
 * <b>⚠️ 本表用物理删除</b>（不是逻辑删除）：唯一索引 {@code uk_user_id_sku_id(user_id, sku_id, deleted)}
 * 的两个唯一列都是关联 ID，无法用「删除时改写唯一列」绕开「同一 SKU 反复加购又删除」的撞键问题；
 * 而购物车项是可完全重建的临时数据，物理删除不丢任何不可再生信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oms_cart_item")
public class OmsCartItem extends BaseEntity {

    /** 商家ID，多商户预留 */
    private Long merchantId;

    /** 会员 ID */
    private Long userId;

    /** SPU ID（冗余，便于按商品分组展示） */
    private Long spuId;

    /** SKU ID */
    private Long skuId;

    /** 购买数量 */
    private Integer quantity;

    /** 是否勾选结算：1 勾选，0 未勾选 */
    private Integer selected;

    /** 加入购物车时的单价快照（分） */
    private Long price;

    /** 商品名称快照 */
    private String skuName;

    /** 商品图片快照 */
    private String skuImage;

    /** 规格摘要快照 */
    private String specDesc;

    /** 加购时 SPU 状态快照：1 上架，0 下架（用于提示「商品已下架」） */
    private Integer spuStatus;
}
