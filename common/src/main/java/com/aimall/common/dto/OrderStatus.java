package com.aimall.common.dto;

/** 订单状态机: CREATED -> PENDING_PAYMENT -> PAID ; CREATED/PENDING_PAYMENT -> CANCELLED */
public final class OrderStatus {
    public static final int CREATED = 0;          // 已创建, 等待库存扣减结果
    public static final int PENDING_PAYMENT = 1;  // 库存已扣减, 待支付
    public static final int PAID = 2;             // 已支付
    public static final int CANCELLED = 3;        // 已取消 (用户取消 / 超时 / 库存不足)

    private OrderStatus() {
    }

    public static String text(int status) {
        return switch (status) {
            case CREATED -> "库存锁定中";
            case PENDING_PAYMENT -> "待支付";
            case PAID -> "已支付";
            case CANCELLED -> "已取消";
            default -> "未知";
        };
    }
}
