package com.aimall.product;

import com.aimall.common.dto.ProductDetail;
import com.aimall.common.dto.ProductView;
import com.aimall.common.dto.Result;
import com.aimall.common.web.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 对外接口 (经网关 /api/products/**)。 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository repo;
    private final ProductService service;

    public ProductController(ProductRepository repo, ProductService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minRamGb,
            @RequestParam(required = false) Integer minStorageGb,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 50));
        var criteria = new ProductRepository.SearchCriteria(keyword, category, brand, tag,
                minPrice, maxPrice, minRamGb, minStorageGb, false);
        List<ProductView> items = repo.searchProducts(criteria, safeSize, (safePage - 1) * safeSize);
        long total = repo.countProducts(criteria);
        return Result.ok(Map.of("total", total, "page", safePage, "size", safeSize, "items", items));
    }

    @GetMapping("/{id}")
    public Result<ProductDetail> detail(@PathVariable long id) {
        return Result.ok(service.detail(id).orElseThrow(() -> new BizException(404, "商品不存在")));
    }
}
