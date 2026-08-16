package com.knox.galaxy.repository;

import com.knox.galaxy.model.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregate reads for the Reports and Dashboard screens.
 *
 * <p>Native SQL rather than JPQL: these are GROUP BY roll-ups using
 * {@code date_trunc}, which JPQL expresses badly. Table names are deliberately
 * unqualified — the connection's search_path is already set to the caller's
 * tenant schema, so one statement reads the right rows for every tenant.
 *
 * <p><strong>Revenue counts delivered orders only.</strong> Cancelled, returned
 * and refunded orders contribute nothing, and in-flight orders aren't money yet.
 * Every query here takes explicit bounds — none binds a null.
 *
 * <p>Two Hibernate quirks shape how this SQL is written, and both fail only at
 * execution time — nothing here is checked at compile or startup:
 * <ul>
 *   <li><strong>Every selected column carries an explicit alias.</strong>
 *       Hibernate auto-discovers column names for native queries and rejects
 *       duplicates. Postgres names every unaliased {@code COUNT(*)} column
 *       "count", so two of them in one SELECT collide with
 *       {@code NonUniqueDiscoveredSqlAliasException}.</li>
 *   <li><strong>Casts use {@code CAST(x AS type)}, never {@code x::type}.</strong>
 *       Hibernate scans native SQL for {@code :name} parameter markers and
 *       mis-reads the second colon of {@code ::} as the start of one.</li>
 * </ul>
 */
public interface ReportRepository extends Repository<Order, Long> {

    // ------------------------------------------------------------------ sales

    /** [revenue, cost, deliveredOrders, totalOrders] over the window. */
    @Query(value =
            "SELECT "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.unit_price * oi.quantity END), 0) AS revenue, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.purchase_price * oi.quantity END), 0) AS cost, "
            + "  COUNT(DISTINCT CASE WHEN o.status = 'delivered' THEN o.id END) AS delivered_orders, "
            + "  COUNT(DISTINCT o.id) AS total_orders "
            + "FROM orders o LEFT JOIN order_items oi ON oi.order_id = o.id "
            + "WHERE o.ordered_at >= :from AND o.ordered_at < :to",
            nativeQuery = true)
    List<Object[]> salesTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [month, revenue, cost, orderCount] per month, oldest first. */
    @Query(value =
            "SELECT CAST(date_trunc('month', o.ordered_at) AS date) AS month, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.unit_price * oi.quantity END), 0) AS revenue, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.purchase_price * oi.quantity END), 0) AS cost, "
            + "  COUNT(DISTINCT o.id) AS order_count "
            + "FROM orders o LEFT JOIN order_items oi ON oi.order_id = o.id "
            + "WHERE o.ordered_at >= :from AND o.ordered_at < :to "
            + "GROUP BY 1 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> salesByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [status, count] — drives the status breakdown. */
    @Query(value =
            "SELECT CAST(o.status AS text) AS status, COUNT(*) AS order_count FROM orders o "
            + "WHERE o.ordered_at >= :from AND o.ordered_at < :to "
            + "GROUP BY 1 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> orderCountsByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [month, cancelled, returned] per month — the lost-orders trend. */
    @Query(value =
            "SELECT CAST(date_trunc('month', o.ordered_at) AS date) AS month, "
            + "  COUNT(*) FILTER (WHERE o.status = 'cancelled') AS cancelled, "
            + "  COUNT(*) FILTER (WHERE o.status IN ('returned','refunded')) AS returned "
            + "FROM orders o WHERE o.ordered_at >= :from AND o.ordered_at < :to "
            + "GROUP BY 1 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> lostOrdersByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [orderCode, status, customerName, reason, orderedAt] for lost orders. */
    @Query(value =
            "SELECT o.order_code, CAST(o.status AS text), c.name, o.status_reason, o.ordered_at "
            + "FROM orders o JOIN customers c ON c.id = o.customer_id "
            + "WHERE o.status IN ('cancelled','returned','refunded') "
            + "  AND o.ordered_at >= :from AND o.ordered_at < :to "
            + "ORDER BY o.ordered_at DESC",
            nativeQuery = true)
    List<Object[]> lostOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ------------------------------------------------------------------ stock

    /**
     * [productId, code, name, onHand, purchasePrice, sellingPrice,
     *  lowStockThreshold, unitsSold] for every product.
     *
     * <p>{@code unitsSold} counts delivered lines only and is what separates
     * "dead stock" (never sold) from merely slow-moving.
     */
    @Query(value =
            "SELECT p.id, p.product_code, p.name, "
            + "  COALESCE(inv.on_hand, 0) AS on_hand, "
            + "  p.purchase_price, p.selling_price, p.low_stock_threshold, "
            + "  COALESCE(sold.units, 0) AS units_sold "
            + "FROM products p "
            + "LEFT JOIN (SELECT product_id, SUM(on_hand) AS on_hand FROM inventory GROUP BY product_id) inv "
            + "       ON inv.product_id = p.id "
            + "LEFT JOIN (SELECT oi.product_id, SUM(oi.quantity) AS units "
            + "           FROM order_items oi JOIN orders o ON o.id = oi.order_id "
            + "           WHERE o.status = 'delivered' GROUP BY oi.product_id) sold "
            + "       ON sold.product_id = p.id "
            + "ORDER BY p.name",
            nativeQuery = true)
    List<Object[]> stockByProduct();

    // -------------------------------------------------------------- customers

    /** [city, orders, revenue] for delivered orders, richest city first. */
    @Query(value =
            "SELECT COALESCE(ci.name, 'Unspecified') AS city, "
            + "  COUNT(DISTINCT o.id) AS orders, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.unit_price * oi.quantity END), 0) AS revenue "
            + "FROM orders o "
            + "JOIN customers c ON c.id = o.customer_id "
            + "LEFT JOIN cities ci ON ci.id = c.city_id "
            + "LEFT JOIN order_items oi ON oi.order_id = o.id "
            + "WHERE o.ordered_at >= :from AND o.ordered_at < :to "
            + "GROUP BY 1 ORDER BY 3 DESC",
            nativeQuery = true)
    List<Object[]> ordersByCity(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** [customerId, name, phone, city, orderCount, spend] — biggest spenders first. */
    @Query(value =
            "SELECT c.id, c.name, c.phone, COALESCE(ci.name, '') AS city, "
            + "  COUNT(DISTINCT o.id) AS order_count, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.unit_price * oi.quantity END), 0) AS spend "
            + "FROM customers c "
            + "LEFT JOIN cities ci ON ci.id = c.city_id "
            + "LEFT JOIN orders o ON o.customer_id = c.id AND o.ordered_at >= :from AND o.ordered_at < :to "
            + "LEFT JOIN order_items oi ON oi.order_id = o.id "
            + "GROUP BY c.id, c.name, c.phone, ci.name ORDER BY 6 DESC, c.name",
            nativeQuery = true)
    List<Object[]> customerTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ------------------------------------------------------------------ users

    /**
     * [userId, name, role, orders, delivered, cancelled, returned, revenue,
     *  profit, units] for staff who placed orders in the window.
     */
    @Query(value =
            "SELECT u.id, u.first_name || ' ' || u.last_name AS name, r.name AS role, "
            + "  COUNT(DISTINCT o.id) AS orders, "
            + "  COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'delivered') AS delivered, "
            + "  COUNT(DISTINCT o.id) FILTER (WHERE o.status = 'cancelled') AS cancelled, "
            + "  COUNT(DISTINCT o.id) FILTER (WHERE o.status IN ('returned','refunded')) AS returned, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.unit_price * oi.quantity END), 0) AS revenue, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' "
            + "        THEN (oi.unit_price - oi.purchase_price) * oi.quantity END), 0) AS profit, "
            + "  COALESCE(SUM(CASE WHEN o.status = 'delivered' THEN oi.quantity END), 0) AS units "
            + "FROM users u "
            + "JOIN roles r ON r.id = u.role_id "
            + "LEFT JOIN orders o ON o.placed_by = u.id AND o.ordered_at >= :from AND o.ordered_at < :to "
            + "LEFT JOIN order_items oi ON oi.order_id = o.id "
            + "GROUP BY u.id, u.first_name, u.last_name, r.name ORDER BY 8 DESC, name",
            nativeQuery = true)
    List<Object[]> userPerformance(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // --------------------------------------------------------------- dashboard

    /** [lowStockCount, outOfStockCount] against each product's own threshold. */
    @Query(value =
            "SELECT "
            + "  COUNT(*) FILTER (WHERE stock.on_hand <= COALESCE(p.low_stock_threshold, 5) AND stock.on_hand > 0) AS low_stock, "
            + "  COUNT(*) FILTER (WHERE stock.on_hand = 0) AS out_of_stock "
            + "FROM products p "
            + "LEFT JOIN LATERAL (SELECT COALESCE(SUM(i.on_hand), 0) AS on_hand "
            + "                   FROM inventory i WHERE i.product_id = p.id) stock ON TRUE",
            nativeQuery = true)
    List<Object[]> stockAlertCounts();
}
