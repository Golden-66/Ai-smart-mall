package com.aimall.product;

import com.aimall.common.event.MqConstants;
import com.aimall.common.event.OrderCreatedEvent;
import com.aimall.common.event.OrderReleaseEvent;
import com.aimall.common.event.StockItem;
import com.aimall.common.event.StockResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 订单创建 -> 扣减库存 -> 回传结果; 订单取消/超时 -> 归还库存。 */
@Component
public class StockListener {
    private static final Logger log = LoggerFactory.getLogger(StockListener.class);

    private final StockService stockService;
    private final ProductService productService;
    private final RabbitTemplate rabbit;

    public StockListener(StockService stockService, ProductService productService, RabbitTemplate rabbit) {
        this.stockService = stockService;
        this.productService = productService;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = MqConstants.Q_STOCK_DEDUCT)
    public void onOrderCreated(OrderCreatedEvent event) {
        String reason = null;
        try {
            stockService.deduct(event.orderNo(), event.items());
        } catch (StockService.InsufficientStockException e) {
            stockService.markFailed(event.orderNo());
            reason = e.getMessage();
            log.info("订单 {} 扣减库存失败: {}", event.orderNo(), reason);
        }
        boolean success = stockService.isDeducted(event.orderNo());
        if (reason == null && !success) {
            reason = "库存扣减失败";
        }
        productService.evictBySkus(skuIds(event.items()));
        rabbit.convertAndSend(MqConstants.EXCHANGE, MqConstants.RK_STOCK_RESULT,
                new StockResultEvent(event.orderNo(), success, reason));
    }

    @RabbitListener(queues = MqConstants.Q_STOCK_RELEASE)
    public void onOrderRelease(OrderReleaseEvent event) {
        boolean released = stockService.release(event.orderNo(), event.items());
        if (released) {
            log.info("订单 {} 已归还库存 ({})", event.orderNo(), event.reason());
            productService.evictBySkus(skuIds(event.items()));
        }
    }

    private static List<Long> skuIds(List<StockItem> items) {
        return items.stream().map(StockItem::skuId).toList();
    }
}
