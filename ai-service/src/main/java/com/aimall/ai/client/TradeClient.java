package com.aimall.ai.client;

import com.aimall.common.dto.CreateOrderReq;
import com.aimall.common.dto.OrderView;
import com.aimall.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "trade-service", contextId = "aiTradeClient")
public interface TradeClient {

    /** 创建订单, 与用户在页面上下单走同一个接口 (同样的校验、同样的幂等)。 */
    @PostMapping("/orders")
    Result<OrderView> createOrder(@RequestHeader("X-User-Id") long userId,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @RequestBody CreateOrderReq req);
}
