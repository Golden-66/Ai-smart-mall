package com.aimall.ai.client;

import com.aimall.common.dto.ProductDetail;
import com.aimall.common.dto.ProductView;
import com.aimall.common.dto.SkuView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "product-service", contextId = "aiProductClient")
public interface ProductClient {

    @GetMapping("/internal/products/search")
    List<SkuView> search(@RequestParam(value = "keyword", required = false) String keyword,
                         @RequestParam(value = "category", required = false) String category,
                         @RequestParam(value = "brand", required = false) String brand,
                         @RequestParam(value = "tag", required = false) String tag,
                         @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                         @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                         @RequestParam(value = "minRamGb", required = false) Integer minRamGb,
                         @RequestParam(value = "minStorageGb", required = false) Integer minStorageGb,
                         @RequestParam(value = "sort", required = false) String sort,
                         @RequestParam(value = "limit", required = false) Integer limit);

    @GetMapping("/internal/skus")
    List<SkuView> skus(@RequestParam("ids") List<Long> ids);

    @GetMapping("/internal/products/by-model")
    ProductView byModel(@RequestParam("modelNo") String modelNo);

    @GetMapping("/internal/products/{id}")
    ProductDetail detail(@PathVariable("id") long id);
}
