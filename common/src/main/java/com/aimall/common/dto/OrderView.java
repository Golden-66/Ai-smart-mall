package com.aimall.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderView(
        String orderNo,
        Integer status,
        String statusText,
        BigDecimal totalAmount,
        String remark,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        List<OrderItemView> items) {
}
