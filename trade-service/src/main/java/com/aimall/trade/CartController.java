package com.aimall.trade;

import com.aimall.common.dto.OrderView;
import com.aimall.common.dto.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
public class CartController {

    public record AddReq(Long skuId, Integer quantity) {
    }

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Result<CartService.CartView> list(@RequestHeader("X-User-Id") long userId) {
        return Result.ok(cartService.list(userId));
    }

    @PostMapping("/items")
    public Result<Void> add(@RequestHeader("X-User-Id") long userId, @RequestBody AddReq req) {
        cartService.add(userId, req.skuId(), req.quantity() == null ? 1 : req.quantity());
        return Result.ok();
    }

    @PutMapping("/items/{skuId}")
    public Result<Void> update(@RequestHeader("X-User-Id") long userId, @PathVariable long skuId,
                               @RequestParam int quantity) {
        cartService.setQuantity(userId, skuId, quantity);
        return Result.ok();
    }

    @DeleteMapping("/items/{skuId}")
    public Result<Void> remove(@RequestHeader("X-User-Id") long userId, @PathVariable long skuId) {
        cartService.remove(userId, skuId);
        return Result.ok();
    }

    @PostMapping("/checkout")
    public Result<OrderView> checkout(@RequestHeader("X-User-Id") long userId,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return Result.ok(cartService.checkout(userId, idemKey));
    }
}
