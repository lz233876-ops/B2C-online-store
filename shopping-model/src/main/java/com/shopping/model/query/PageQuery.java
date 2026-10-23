package com.shopping.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分页查询基类。
 * <p>
 * <b>为什么要在这里夹住 size 上限：</b>
 * 如果直接信任前端传来的 {@code size}，一个 {@code GET /page?size=1000000}
 * 就能让数据库把全表捞出来、把 JVM 内存打满——这是一个<b>不需要任何技巧的
 * 拒绝服务攻击</b>。所以上限必须在入口处强制，而不是指望前端自觉。
 * <p>
 * 同时 MyBatis-Plus 的分页插件也配了 {@code maxLimit(100)}，
 * 两层防护：即使有人绕过本类直接构造 {@code Page} 对象，也拦得住。
 * <p>
 * 通过重写 getter 而不是在 setter 里修正，是为了让「传进来的值」和
 * 「实际使用的值」分离——排查问题时能看到原始入参。
 */
@Data
@Schema(description = "分页查询基类")
public class PageQuery {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 10L;
    private static final long MAX_SIZE = 100L;

    @Schema(description = "当前页，从 1 开始", defaultValue = "1")
    private Long current = DEFAULT_CURRENT;

    @Schema(description = "每页条数，最大 100", defaultValue = "10")
    private Long size = DEFAULT_SIZE;

    public Long getCurrent() {
        return (current == null || current < 1) ? DEFAULT_CURRENT : current;
    }

    public Long getSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 由 current / size 派生的偏移量，手写 SQL 时使用 */
    public long offset() {
        return (getCurrent() - 1) * getSize();
    }
}
