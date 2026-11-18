package com.aimall.common.event;

public final class MqConstants {
    public static final String EXCHANGE = "mall.order.exchange";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_STOCK_RESULT = "stock.result";
    public static final String RK_ORDER_PAY = "order.pay";
    public static final String RK_ORDER_RELEASE = "order.release";
    public static final String RK_ORDER_TIMEOUT = "order.timeout";

    public static final String Q_STOCK_DEDUCT = "mall.stock.deduct";
    public static final String Q_STOCK_RELEASE = "mall.stock.release";
    public static final String Q_ORDER_STOCK_RESULT = "mall.order.stock-result";
    public static final String Q_ORDER_PAY = "mall.order.pay";
    /** 带 TTL 的延迟队列, 过期后经死信交换机路由到 Q_ORDER_TIMEOUT。 */
    public static final String Q_ORDER_TIMEOUT_DELAY = "mall.order.timeout.delay";
    public static final String Q_ORDER_TIMEOUT = "mall.order.timeout";

    public static final String EVENT_PACKAGE = "com.aimall.common.event";

    private MqConstants() {
    }
}
