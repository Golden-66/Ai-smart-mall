package com.aimall.product;

import com.aimall.common.dto.ProductDetail;
import com.aimall.common.dto.ProductView;
import com.aimall.common.dto.SkuView;
import com.aimall.common.web.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 服务间调用 (OpenFeign), 网关没有路由到 /internal/**, 外部无法访问。 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final ProductRepository repo;
    private final ProductService service;

    public InternalController(ProductRepository repo, ProductService service) {
        this.repo = repo;
        this.service = service;
    }

    /** 实时价格与库存 (不走缓存)。 */
    @GetMapping("/skus")
    public List<SkuView> skus(@RequestParam("ids") List<Long> ids) {
        return repo.findSkusByIds(ids);
    }

    @GetMapping("/products/search")
    public List<SkuView> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minRamGb,
            @RequestParam(required = false) Integer minStorageGb,
            @RequestParam(defaultValue = "true") boolean inStockOnly,
            @RequestParam(defaultValue = "price_desc") String sort,
            @RequestParam(defaultValue = "10") int limit) {
        var criteria = new ProductRepository.SearchCriteria(keyword, category, brand, tag,
                minPrice, maxPrice, minRamGb, minStorageGb, inStockOnly);
        return service.searchSkus(criteria, !"price_asc".equals(sort), limit, 2);
    }

    @GetMapping("/products/by-model")
    public ProductView byModel(@RequestParam("modelNo") String modelNo) {
        return repo.findProductByModelNo(modelNo).orElseThrow(() -> new BizException(404, "型号不存在: " + modelNo));
    }

    @GetMapping("/products/{id}")
    public ProductDetail detail(@PathVariable long id) {
        return service.detail(id).orElseThrow(() -> new BizException(404, "商品不存在"));
    }
}
