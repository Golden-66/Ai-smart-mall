package com.aimall.common.event;

/** 延迟队列消息: 订单支付超时检查。 */
public record OrderTimeoutMessage(String orderNo) {
}
