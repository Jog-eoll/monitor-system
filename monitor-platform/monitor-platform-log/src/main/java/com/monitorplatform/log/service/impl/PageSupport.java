package com.monitorplatform.log.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.entity.PageDTO;

final class PageSupport {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 20L;
    private static final long MAX_SIZE = 100L;

    private PageSupport() {
    }

    static long current(PageDTO pageDTO) {
        if (pageDTO == null || pageDTO.getCurrent() == null || pageDTO.getCurrent() < 1) {
            return DEFAULT_CURRENT;
        }
        return pageDTO.getCurrent();
    }

    static long size(PageDTO pageDTO) {
        if (pageDTO == null || pageDTO.getSize() == null || pageDTO.getSize() < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(pageDTO.getSize(), MAX_SIZE);
    }

    static <T> Page<T> page(PageDTO pageDTO) {
        return new Page<T>(current(pageDTO), size(pageDTO));
    }
}
