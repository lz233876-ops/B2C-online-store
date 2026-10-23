package com.shopping.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应体。
 * <p>
 * 刻意<b>不</b>依赖 MyBatis-Plus 的 {@code IPage}：common 层必须零持久层依赖（由 Maven Enforcer 强制），
 * 所以由 service 层负责把 {@code IPage} 转换成本类。
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页，从 1 开始 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页数据 */
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long current, long size, long total, List<T> records) {
        this.current = current;
        this.size = size;
        this.total = total;
        this.records = records == null ? Collections.emptyList() : records;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        return new PageResult<>(current, size, total, records);
    }

    /** 空分页结果，用于「查询无数据」的早返回，避免调用方写 null 判断 */
    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(current, size, 0L, Collections.emptyList());
    }
}
