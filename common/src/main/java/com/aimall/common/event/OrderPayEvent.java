package com.aimall.common.event;

import java.math.BigDecimal;

/** 支付请求事件 (模拟支付渠道的异步回调)。 */
public record OrderPayEvent(String orderNo, Long userId, BigDecimal amount) {
}
