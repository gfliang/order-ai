package com.example.orderai.order.dto;

import lombok.Data;

import java.util.List;

/** 分页结果 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNo;
    private long pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNo, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.records = records;
        result.total = total;
        result.pageNo = pageNo;
        result.pageSize = pageSize;
        return result;
    }
}
