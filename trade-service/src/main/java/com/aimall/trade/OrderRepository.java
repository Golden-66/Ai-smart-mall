package com.aimall.trade;

import com.aimall.common.dto.OrderItemView;
import com.aimall.common.dto.OrderStatus;
import com.aimall.common.dto.OrderView;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    public record OrderRow(String orderNo, long userId, int status, BigDecimal totalAmount, String remark,
                           String cancelReason, LocalDateTime createdAt, LocalDateTime paidAt) {
    }

    public record ItemRow(long skuId, long productId, String productName, String skuName,
                          BigDecimal price, int quantity) {
    }

    private static final String ORDER_COLS =
            "order_no, user_id, status, total_amount, remark, cancel_reason, created_at, paid_at";

    private static final RowMapper<OrderRow> ORDER_MAPPER = (rs, i) -> new OrderRow(
            rs.getString("order_no"), rs.getLong("user_id"), rs.getInt("status"),
            rs.getBigDecimal("total_amount"), rs.getString("remark"), rs.getString("cancel_reason"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toLocalDateTime());

    private static final RowMapper<ItemRow> ITEM_MAPPER = (rs, i) -> new ItemRow(
            rs.getLong("sku_id"), rs.getLong("product_id"), rs.getString("product_name"),
            rs.getString("sku_name"), rs.getBigDecimal("price"), rs.getInt("quantity"));

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 订单主表 + 明细在同一个本地事务内写入。 */
    @Transactional
    public void createOrder(String orderNo, long userId, BigDecimal total, String remark, List<ItemRow> items) {
        jdbc.sql("INSERT INTO orders(order_no, user_id, status, total_amount, remark) VALUES (:o, :u, :s, :t, :r)")
                .param("o", orderNo).param("u", userId).param("s", OrderStatus.CREATED)
                .param("t", total).param("r", remark).update();
        for (ItemRow it : items) {
            jdbc.sql("INSERT INTO order_item(order_no, sku_id, product_id, product_name, sku_name, price, quantity) "
                            + "VALUES (:o, :sku, :pid, :pn, :sn, :p, :q)")
                    .param("o", orderNo).param("sku", it.skuId()).param("pid", it.productId())
                    .param("pn", it.productName()).param("sn", it.skuName())
                    .param("p", it.price()).param("q", it.quantity()).update();
        }
    }

    public Optional<OrderRow> findOrder(String orderNo) {
        return jdbc.sql("SELECT " + ORDER_COLS + " FROM orders WHERE order_no = :o")
                .param("o", orderNo).query(ORDER_MAPPER).optional();
    }

    public List<ItemRow> findItems(String orderNo) {
        return jdbc.sql("SELECT sku_id, product_id, product_name, sku_name, price, quantity FROM order_item WHERE order_no = :o")
                .param("o", orderNo).query(ITEM_MAPPER).list();
    }

    public List<OrderRow> listByUser(long userId, int limit, int offset) {
        return jdbc.sql("SELECT " + ORDER_COLS + " FROM orders WHERE user_id = :u ORDER BY created_at DESC, id DESC LIMIT :l OFFSET :f")
                .param("u", userId).param("l", limit).param("f", offset).query(ORDER_MAPPER).list();
    }

    /** CAS 状态流转: 只有当前状态在 from 集合内才会更新, 保证并发/重复消息下状态机不会乱。 */
    public int casStatus(String orderNo, List<Integer> from, int to, String cancelReason) {
        return jdbc.sql("UPDATE orders SET status = :to, cancel_reason = COALESCE(:reason, cancel_reason) "
                        + "WHERE order_no = :o AND status IN (:from)")
                .param("to", to).param("reason", cancelReason).param("o", orderNo).param("from", from).update();
    }

    public int markPaid(String orderNo) {
        return jdbc.sql("UPDATE orders SET status = :to, paid_at = NOW() WHERE order_no = :o AND status = :from")
                .param("to", OrderStatus.PAID).param("o", orderNo).param("from", OrderStatus.PENDING_PAYMENT).update();
    }

    // ---------------------------------------------------------------- payment
    public int insertPaymentIfAbsent(String orderNo, long userId, BigDecimal amount) {
        return jdbc.sql("INSERT IGNORE INTO payment(order_no, user_id, amount, status) VALUES (:o, :u, :a, 0)")
                .param("o", orderNo).param("u", userId).param("a", amount).update();
    }

    public int updatePaymentStatus(String orderNo, int status) {
        return jdbc.sql("UPDATE payment SET status = :s WHERE order_no = :o")
                .param("s", status).param("o", orderNo).update();
    }

    // ---------------------------------------------------------------- 补偿任务
    public List<OrderRow> findStale(List<Integer> statuses, LocalDateTime before, int limit) {
        return jdbc.sql("SELECT " + ORDER_COLS + " FROM orders WHERE status IN (:st) AND created_at < :before "
                        + "ORDER BY created_at ASC LIMIT :l")
                .param("st", statuses).param("before", before).param("l", limit).query(ORDER_MAPPER).list();
    }

    // ---------------------------------------------------------------- 转换
    public static OrderView toView(OrderRow o, List<ItemRow> items) {
        List<OrderItemView> views = items.stream()
                .map(i -> new OrderItemView(i.skuId(), i.productId(), i.productName(), i.skuName(), i.price(), i.quantity()))
                .toList();
        return new OrderView(o.orderNo(), o.status(), OrderStatus.text(o.status()), o.totalAmount(), o.remark(),
                o.cancelReason(), o.createdAt(), o.paidAt(), views);
    }
}
