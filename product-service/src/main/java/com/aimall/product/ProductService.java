package com.aimall.product;

import com.aimall.common.dto.ProductDetail;
import com.aimall.common.dto.SkuView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String KEY_PREFIX = "product:detail:";
    private static final String NULL_MARK = "NULL";

    private final ProductRepository repo;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final long ttlSeconds;

    public ProductService(ProductRepository repo, StringRedisTemplate redis, ObjectMapper mapper,
                          @Value("${mall.cache.product-ttl-seconds:600}") long ttlSeconds) {
        this.repo = repo;
        this.redis = redis;
        this.mapper = mapper;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 商品详情: Cache-Aside。
     * - 空值缓存 (短 TTL) 防止缓存穿透;
     * - TTL 加随机抖动, 防止大量 key 同时过期造成雪崩;
     * - 库存变化后主动失效 (见 evict)。
     */
    public Optional<ProductDetail> detail(long productId) {
        String key = KEY_PREFIX + productId;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return NULL_MARK.equals(cached) ? Optional.empty()
                        : Optional.of(mapper.readValue(cached, ProductDetail.class));
            }
        } catch (Exception e) {
            log.warn("read product cache failed: {}", e.getMessage());
        }

        Optional<ProductDetail> loaded = repo.findProductById(productId)
                .map(p -> new ProductDetail(p, repo.findSkusByProductId(productId)));
        try {
            if (loaded.isPresent()) {
                long jitter = ThreadLocalRandom.current().nextLong(ttlSeconds / 5 + 1);
                redis.opsForValue().set(key, mapper.writeValueAsString(loaded.get()), Duration.ofSeconds(ttlSeconds + jitter));
            } else {
                redis.opsForValue().set(key, NULL_MARK, Duration.ofSeconds(60));
            }
        } catch (Exception e) {
            log.warn("write product cache failed: {}", e.getMessage());
        }
        return loaded;
    }

    public void evictProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        try {
            List<String> keys = new ArrayList<>();
            productIds.forEach(id -> keys.add(KEY_PREFIX + id));
            redis.delete(keys);
        } catch (Exception e) {
            log.warn("evict product cache failed: {}", e.getMessage());
        }
    }

    public void evictBySkus(List<Long> skuIds) {
        evictProducts(repo.productIdsOfSkus(skuIds));
    }

    /**
     * AI 选品用的 SKU 检索: 同一商品最多返回 perProductCap 个 SKU, 避免颜色款占满结果。
     */
    public List<SkuView> searchSkus(ProductRepository.SearchCriteria c, boolean priceDesc, int limit, int perProductCap) {
        int safeLimit = Math.max(1, Math.min(limit, 30));
        List<SkuView> raw = repo.searchSkus(c, priceDesc, safeLimit * 4);
        Map<Long, Integer> perProduct = new HashMap<>();
        List<SkuView> out = new ArrayList<>();
        for (SkuView s : raw) {
            int n = perProduct.merge(s.productId(), 1, Integer::sum);
            if (n <= perProductCap) {
                out.add(s);
                if (out.size() >= safeLimit) {
                    break;
                }
            }
        }
        return out;
    }
}
