package com.crypto.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果对象
 * <p>
 * 用于封装分页查询的结果数据
 *
 * @param <T> 数据类型
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码(从1开始)
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 创建分页结果
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param total   总记录数
     * @param records 数据列表
     * @param <T>     数据类型
     * @return PageResult对象
     */
    public static <T> PageResult<T> of(Long current, Long size, Long total, List<T> records) {
        long pages = (total + size - 1) / size;
        return PageResult.<T>builder()
                .current(current)
                .size(size)
                .total(total)
                .pages(pages)
                .records(records)
                .hasPrevious(current > 1)
                .hasNext(current < pages)
                .build();
    }

    /**
     * 从MyBatis Plus的Page对象创建
     *
     * @param page MyBatis Plus Page对象
     * @param <T>  数据类型
     * @return PageResult对象
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        return of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    /**
     * 创建空的分页结果
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param <T>     数据类型
     * @return PageResult对象
     */
    public static <T> PageResult<T> empty(Long current, Long size) {
        return of(current, size, 0L, List.of());
    }
}
