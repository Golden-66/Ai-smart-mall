package com.aimall.trade;

import com.aimall.common.event.MqConstants;
import com.aimall.common.event.OrderPayEvent;
import com.aimall.common.event.OrderTimeoutMessage;
import com.aimall.common.event.StockResultEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderListener {

    private final OrderService orderService;

    public OrderListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = MqConstants.Q_ORDER_STOCK_RESULT)
    public void onStockResult(StockResultEvent event) {
        orderService.onStockResult(event);
    }

    @RabbitListener(queues = MqConstants.Q_ORDER_PAY)
    public void onPay(OrderPayEvent event) {
        orderService.onPay(event);
    }

    @RabbitListener(queues = MqConstants.Q_ORDER_TIMEOUT)
    public void onTimeout(OrderTimeoutMessage message) {
        orderService.onTimeout(message);
    }
}
