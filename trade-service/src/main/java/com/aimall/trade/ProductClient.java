package com.aimall.trade;

import com.aimall.common.dto.SkuView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", contextId = "tradeProductClient")
public interface ProductClient {

    /** 实时价格与库存, 订单金额一律以此为准, 不信任客户端传入的价格。 */
    @GetMapping("/internal/skus")
    List<SkuView> skus(@RequestParam("ids") List<Long> ids);
}
