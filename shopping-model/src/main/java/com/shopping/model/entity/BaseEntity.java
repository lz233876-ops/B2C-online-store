package com.shopping.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：所有表都有的 4 个公共字段（见 {@code docs/04-数据库设计.md} §1.1）。
 * <p>
 * <b>关于 create_time / update_time 为什么不用 MyBatis-Plus 的自动填充：</b>
 * <p>
 * 数据库里这两列已经是 {@code DEFAULT CURRENT_TIMESTAMP} 和
 * {@code ON UPDATE CURRENT_TIMESTAMP}，也就是说 <b>DB 自己会维护</b>。
 * 如果再配一个 {@code MetaObjectHandler} 在应用层填一遍，就有了<b>两个真相来源</b>：
 * <ul>
 *   <li>用 MP 写入 → 用应用时间；</li>
 *   <li>DBA 手工 UPDATE / 其他脚本写入 → 用 DB 时间。</li>
 * </ul>
 * 两者会出现时区与时钟偏差，排查「这行到底什么时候改的」时非常痛苦。
 * 所以这里<b>统一交给数据库</b>——单一事实源，且任何写入路径都生效。
 * <p>
 * 代价：MP 的 {@code insert} 会忽略 null 字段，所以这两列不会被写进 SQL，
 * 由 DB 填默认值。这一点在 {@code update_time} 上同样成立（{@code updateById}
 * 不把 update_time 放进 SET 子句，由 {@code ON UPDATE} 触发）。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，雪花 ID（分布式友好，且不暴露业务量） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间，由 DB 的 DEFAULT CURRENT_TIMESTAMP 维护 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间，由 DB 的 ON UPDATE CURRENT_TIMESTAMP 维护 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0 未删除，1 已删除。
     * <p>
     * 标注 {@code @TableLogic} 后，MP 会自动：
     * ① 查询时追加 {@code deleted = 0}；② {@code deleteById} 改成 {@code UPDATE ... SET deleted = 1}。
     * <p>
     * ⚠️ <b>流水型表（如 {@code wms_stock_log}）例外</b>：那张表是 append-only，
     * {@code deleted} 恒为 0，代码里禁止任何删除操作。
     */
    @TableLogic
    private Integer deleted;
}
