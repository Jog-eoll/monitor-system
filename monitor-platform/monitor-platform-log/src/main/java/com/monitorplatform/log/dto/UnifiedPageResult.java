package com.monitorplatform.log.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class UnifiedPageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long size;
    private long current;
    private List<OrderItem> orders;
    private boolean optimizeCountSql;
    private boolean searchCount;
    private Long maxLimit;
    private String countId;
    private long pages;
    private long page;
    private long pageSize;
    private long pageNum;

    public static <T> UnifiedPageResult<T> of(IPage<T> page) {
        UnifiedPageResult<T> result = new UnifiedPageResult<>();
        if (page == null) {
            result.setRecords(Collections.emptyList());
            result.setOrders(Collections.emptyList());
            result.setCurrent(1L);
            result.setPage(1L);
            result.setPageNum(1L);
            return result;
        }

        long current = page.getCurrent();
        long size = page.getSize();
        result.setRecords(page.getRecords() == null ? Collections.emptyList() : page.getRecords());
        result.setTotal(page.getTotal());
        result.setSize(size);
        result.setCurrent(current);
        result.setOrders(page.orders() == null ? Collections.emptyList() : page.orders());
        result.setOptimizeCountSql(page.optimizeCountSql());
        result.setSearchCount(page.searchCount());
        result.setMaxLimit(page.maxLimit());
        result.setCountId(page.countId());
        result.setPages(page.getPages());
        result.setPage(current);
        result.setPageSize(size);
        result.setPageNum(current);
        return result;
    }
}
