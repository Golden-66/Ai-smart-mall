package com.aimall.product;

import com.aimall.common.event.StockItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 库存扣减 / 归还。
 * - stock_log 以 order_no 为主键保证幂等 (MQ 至少投递一次, 可能重复);
 * - 扣减使用 "UPDATE ... WHERE stock >= ?" 单条原子 SQL, 防止超卖;
 * - 任意 SKU 库存不足则整笔事务回滚, 订单要么全部扣减要么全部不扣。
 */
@Service
public class StockService {

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(long skuId) {
            super("库存不足 (skuId=" + skuId + ")");
        }
    }

    private final ProductRepository repo;

    public StockService(ProductRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void deduct(String orderNo, List<StockItem> items) {
        if (repo.insertStockLog(orderNo, "DEDUCTED") == 0) {
            return; // 重复消息, 已处理过
        }
        // 按 skuId 排序加锁, 避免并发订单互相死锁
        List<StockItem> sorted = items.stream().sorted(Comparator.comparing(StockItem::skuId)).toList();
        for (StockItem it : sorted) {
            if (repo.deductStock(it.skuId(), it.quantity()) == 0) {
                throw new InsufficientStockException(it.skuId());
            }
        }
    }

    public void markFailed(String orderNo) {
        repo.insertStockLog(orderNo, "FAILED");
    }

    public boolean isDeducted(String orderNo) {
        return repo.findStockLogStatus(orderNo).map("DEDUCTED"::equals).orElse(false);
    }

    /** @return true 表示本次真正归还了库存; false 表示从未扣减或已归还过 (幂等)。 */
    @Transactional
    public boolean release(String orderNo, List<StockItem> items) {
        if (repo.markReleased(orderNo) == 0) {
            return false;
        }
        for (StockItem it : items) {
            repo.restoreStock(it.skuId(), it.quantity());
        }
        return true;
    }
}
