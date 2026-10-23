package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址（{@code ums_user_address}）。
 * <p>
 * <b>⚠️ 默认地址唯一性是「应用层保证」的，不是数据库保证的。</b>
 * 本表只有主键索引，没有 {@code (user_id, default_status)} 之类的唯一键——
 * 因为唯一索引带 {@code deleted} 时无法保证「同一用户只有一个默认地址」
 * （{@code (user_id, default_status, deleted)} 允许存在多条 deleted=0 的默认地址）。
 * <p>
 * 所以设置默认地址必须：<b>在一个事务里先把该用户所有地址置为非默认，再把目标地址置为默认</b>。
 * 顺序不能反（否则中间态会出现两个默认地址），也不能省（否则会出现多个默认）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ums_user_address")
public class UmsUserAddress extends BaseEntity {

    /** 会员 ID，逻辑关联 {@code ums_user.id} */
    private Long userId;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区/县 */
    private String district;

    /** 详细地址（街道门牌） */
    private String detailAddress;

    /** 邮政编码 */
    private String postalCode;

    /** 是否默认：1 是，0 否 */
    private Integer defaultStatus;

    /** 标签：家 / 公司 / 学校 */
    private String tag;
}
