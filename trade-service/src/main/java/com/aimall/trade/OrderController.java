package com.aimall.trade;

import com.aimall.common.dto.CreateOrderReq;
import com.aimall.common.dto.OrderView;
import com.aimall.common.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 经网关 /api/orders/** 访问; ai-service 也通过 Feign 调用 POST /orders (传入 X-User-Id)。 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OrderView> create(@RequestHeader("X-User-Id") long userId,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                    @RequestBody CreateOrderReq req) {
        return Result.ok(orderService.create(userId, req.items(), req.remark(), idemKey));
    }

    @GetMapping
    public Result<List<OrderView>> list(@RequestHeader("X-User-Id") long userId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return Result.ok(orderService.list(userId, page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderView> get(@RequestHeader("X-User-Id") long userId, @PathVariable String orderNo) {
        return Result.ok(orderService.get(userId, orderNo));
    }

    @PostMapping("/{orderNo}/pay")
    public Result<Map<String, Object>> pay(@RequestHeader("X-User-Id") long userId, @PathVariable String orderNo) {
        return Result.ok(orderService.pay(userId, orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@RequestHeader("X-User-Id") long userId, @PathVariable String orderNo) {
        orderService.cancelByUser(userId, orderNo);
        return Result.ok();
    }
}
