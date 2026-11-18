package com.aimall.product;

import com.aimall.common.dto.ProductView;
import com.aimall.common.dto.SkuView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProductRepository {

    /** 检索条件, 公开接口与 AI 工具共用。 */
    public record SearchCriteria(String keyword, String category, String brand, String tag,
                                 BigDecimal minPrice, BigDecimal maxPrice,
                                 Integer minRamGb, Integer minStorageGb, boolean inStockOnly) {
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    private static final String PRODUCT_SELECT =
            "SELECT p.id, p.name, p.brand, p.category, p.model_no, p.tags, p.description, p.specs, "
                    + "MIN(s.price) AS min_price, MAX(s.price) AS max_price, SUM(s.stock) AS total_stock "
                    + "FROM product p JOIN sku s ON s.product_id = p.id";

    private static final String SKU_SELECT =
            "SELECT s.id AS sku_id, s.product_id, p.name AS product_name, p.brand, p.category, p.model_no, "
                    + "s.sku_name, s.ram_gb, s.storage_gb, s.price, s.stock, p.tags "
                    + "FROM sku s JOIN product p ON p.id = s.product_id";

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ProductView> productMapper;
    private final RowMapper<SkuView> skuMapper = (rs, i) -> new SkuView(
            rs.getLong("sku_id"), rs.getLong("product_id"), rs.getString("product_name"),
            rs.getString("brand"), rs.getString("category"), rs.getString("model_no"),
            rs.getString("sku_name"), rs.getObject("ram_gb", Integer.class),
            rs.getObject("storage_gb", Integer.class), rs.getBigDecimal("price"),
            rs.getInt("stock"), rs.getString("tags"));

    public ProductRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.productMapper = (rs, i) -> new ProductView(
                rs.getLong("id"), rs.getString("name"), rs.getString("brand"), rs.getString("category"),
                rs.getString("model_no"), rs.getString("tags"), rs.getString("description"),
                parseSpecs(rs.getString("specs")), rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price"), rs.getInt("total_stock"));
    }

    private Map<String, Object> parseSpecs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------ 查询
    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Where buildWhere(SearchCriteria c) {
        StringBuilder sb = new StringBuilder(" WHERE p.status = 1");
        Map<String, Object> params = new HashMap<>();
        if (has(c.category())) {
            sb.append(" AND p.category = :category");
            params.put("category", c.category());
        }
        if (has(c.brand())) {
            sb.append(" AND p.brand = :brand");
            params.put("brand", c.brand());
        }
        if (has(c.keyword())) {
            sb.append(" AND (p.name LIKE :kw OR p.model_no LIKE :kw OR p.tags LIKE :kw "
                    + "OR p.brand LIKE :kw OR p.description LIKE :kw)");
            params.put("kw", "%" + escapeLike(c.keyword().trim()) + "%");
        }
        if (has(c.tag())) {
            sb.append(" AND p.tags LIKE :tag");
            params.put("tag", "%" + escapeLike(c.tag().trim()) + "%");
        }
        if (c.minPrice() != null) {
            sb.append(" AND s.price >= :minPrice");
            params.put("minPrice", c.minPrice());
        }
        if (c.maxPrice() != null) {
            sb.append(" AND s.price <= :maxPrice");
            params.put("maxPrice", c.maxPrice());
        }
        if (c.minRamGb() != null) {
            sb.append(" AND s.ram_gb >= :minRam");
            params.put("minRam", c.minRamGb());
        }
        if (c.minStorageGb() != null) {
            sb.append(" AND s.storage_gb >= :minStorage");
            params.put("minStorage", c.minStorageGb());
        }
        if (c.inStockOnly()) {
            sb.append(" AND s.stock > 0");
        }
        return new Where(sb.toString(), params);
    }

    public List<ProductView> searchProducts(SearchCriteria c, int limit, int offset) {
        Where w = buildWhere(c);
        Map<String, Object> params = new HashMap<>(w.params());
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(PRODUCT_SELECT + w.sql() + " GROUP BY p.id ORDER BY min_price ASC, p.id ASC LIMIT :limit OFFSET :offset")
                .params(params).query(productMapper).list();
    }

    public long countProducts(SearchCriteria c) {
        Where w = buildWhere(c);
        return jdbc.sql("SELECT COUNT(DISTINCT p.id) FROM product p JOIN sku s ON s.product_id = p.id" + w.sql())
                .params(w.params()).query(Long.class).single();
    }

    /** SKU 维度检索, AI 选品使用。 */
    public List<SkuView> searchSkus(SearchCriteria c, boolean priceDesc, int limit) {
        Where w = buildWhere(c);
        Map<String, Object> params = new HashMap<>(w.params());
        params.put("limit", limit);
        return jdbc.sql(SKU_SELECT + w.sql() + " ORDER BY s.price " + (priceDesc ? "DESC" : "ASC") + ", s.id ASC LIMIT :limit")
                .params(params).query(skuMapper).list();
    }

    public List<SkuView> findSkusByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SKU_SELECT + " WHERE s.id IN (:ids)").param("ids", ids).query(skuMapper).list();
    }

    public List<SkuView> findSkusByProductId(long productId) {
        return jdbc.sql(SKU_SELECT + " WHERE s.product_id = :pid ORDER BY s.price ASC, s.id ASC")
                .param("pid", productId).query(skuMapper).list();
    }

    public Optional<ProductView> findProductById(long id) {
        return jdbc.sql(PRODUCT_SELECT + " WHERE p.id = :id AND p.status = 1 GROUP BY p.id")
                .param("id", id).query(productMapper).optional();
    }

    public Optional<ProductView> findProductByModelNo(String modelNo) {
        return jdbc.sql(PRODUCT_SELECT + " WHERE p.model_no = :m AND p.status = 1 GROUP BY p.id")
                .param("m", modelNo).query(productMapper).optional();
    }

    public long countAllProducts() {
        return jdbc.sql("SELECT COUNT(*) FROM product").query(Long.class).single();
    }

    public List<Long> productIdsOfSkus(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT DISTINCT product_id FROM sku WHERE id IN (:ids)")
                .param("ids", skuIds).query(Long.class).list();
    }

    // ------------------------------------------------------------ 写入
    public long insertProduct(String name, String brand, String category, String modelNo,
                              String tags, String description, String specsJson) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO product(name, brand, category, model_no, tags, description, specs) "
                        + "VALUES (:name, :brand, :category, :modelNo, :tags, :description, :specs)")
                .param("name", name).param("brand", brand).param("category", category)
                .param("modelNo", modelNo).param("tags", tags).param("description", description)
                .param("specs", specsJson)
                .update(kh);
        return kh.getKey().longValue();
    }

    public void insertSku(long productId, String skuName, String color, Integer ramGb, Integer storageGb,
                          BigDecimal price, int stock) {
        jdbc.sql("INSERT INTO sku(product_id, sku_name, color, ram_gb, storage_gb, price, stock) "
                        + "VALUES (:pid, :name, :color, :ram, :storage, :price, :stock)")
                .param("pid", productId).param("name", skuName).param("color", color)
                .param("ram", ramGb).param("storage", storageGb).param("price", price).param("stock", stock)
                .update();
    }

    // ------------------------------------------------------------ 库存 (原子 SQL, 防超卖)
    public int deductStock(long skuId, int qty) {
        return jdbc.sql("UPDATE sku SET stock = stock - :q WHERE id = :id AND stock >= :q")
                .param("q", qty).param("id", skuId).update();
    }

    public int restoreStock(long skuId, int qty) {
        return jdbc.sql("UPDATE sku SET stock = stock + :q WHERE id = :id")
                .param("q", qty).param("id", skuId).update();
    }

    public int insertStockLog(String orderNo, String status) {
        return jdbc.sql("INSERT IGNORE INTO stock_log(order_no, status) VALUES (:o, :s)")
                .param("o", orderNo).param("s", status).update();
    }

    public Optional<String> findStockLogStatus(String orderNo) {
        return jdbc.sql("SELECT status FROM stock_log WHERE order_no = :o")
                .param("o", orderNo).query(String.class).optional();
    }

    public int markReleased(String orderNo) {
        return jdbc.sql("UPDATE stock_log SET status = 'RELEASED' WHERE order_no = :o AND status = 'DEDUCTED'")
                .param("o", orderNo).update();
    }
}
