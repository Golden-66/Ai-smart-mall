package com.aimall.common.dto;

import java.util.List;

public record CreateOrderReq(List<OrderItemReq> items, String remark) {
}
