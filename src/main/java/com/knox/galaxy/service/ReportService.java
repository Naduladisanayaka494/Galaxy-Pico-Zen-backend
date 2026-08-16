package com.knox.galaxy.service;

import com.knox.galaxy.dto.*;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates behind the Reports tabs and the Overview screen.
 *
 * <p>One rule runs through all of it: <strong>only delivered orders count as
 * money</strong>. Cancelled and returned orders appear in counts and in the
 * lost-orders views, but never in revenue, cost or profit.
 *
 * <p>Windows are half-open — {@code [from, to)} — so a month boundary can't
 * double-count an order that landed exactly at midnight.
 */
@Service
public class ReportService {

    private static final int DEFAULT_WINDOW_MONTHS = 12;
    private static final int RECENT_ORDER_COUNT = 5;

    /** Statuses that still need work — the Overview's "open orders" figure. */
    private static final List<OrderStatus> OPEN_STATUSES = Arrays.asList(
            OrderStatus.processing, OrderStatus.ready_to_ship, OrderStatus.delivering);

    @Autowired private ReportRepository reportRepository;
    @Autowired private FinanceEntryRepository financeEntryRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderService orderService;

    // ------------------------------------------------------------------ sales

    @Transactional(readOnly = true)
    public SalesReportResponse sales(LocalDate from, LocalDate to) {
        LocalDate start = startOrDefault(from);
        LocalDate endExclusive = endOrDefault(to);

        Object[] totals = firstRow(reportRepository.salesTotals(start.atStartOfDay(), endExclusive.atStartOfDay()));
        BigDecimal revenue = decimal(totals, 0);
        BigDecimal cost = decimal(totals, 1);
        long delivered = number(totals, 2);
        long allOrders = number(totals, 3);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Object[] row : reportRepository.orderCountsByStatus(start.atStartOfDay(), endExclusive.atStartOfDay())) {
            statusCounts.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Merge the revenue roll-up with the lost-order roll-up: both are keyed
        // by month but a month can appear in either one alone.
        Map<LocalDate, SalesReportResponse.MonthPoint> byMonth = new TreeMap<>();
        for (Object[] row : reportRepository.salesByMonth(start.atStartOfDay(), endExclusive.atStartOfDay())) {
            LocalDate month = localDate(row[0]);
            BigDecimal monthRevenue = decimal(row, 1);
            BigDecimal monthCost = decimal(row, 2);
            byMonth.put(month, new SalesReportResponse.MonthPoint(
                    month, monthRevenue, monthCost, monthRevenue.subtract(monthCost),
                    ((Number) row[3]).longValue(), 0, 0));
        }
        for (Object[] row : reportRepository.lostOrdersByMonth(start.atStartOfDay(), endExclusive.atStartOfDay())) {
            LocalDate month = localDate(row[0]);
            SalesReportResponse.MonthPoint point = byMonth.computeIfAbsent(month,
                    m -> new SalesReportResponse.MonthPoint(
                            m, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
            point.setCancelled(((Number) row[1]).longValue());
            point.setReturned(((Number) row[2]).longValue());
        }

        List<SalesReportResponse.LostOrder> lost =
                reportRepository.lostOrders(start.atStartOfDay(), endExclusive.atStartOfDay()).stream()
                        .map(row -> new SalesReportResponse.LostOrder(
                                (String) row[0], (String) row[1], (String) row[2],
                                (String) row[3], localDateTime(row[4])))
                        .collect(Collectors.toList());

        BigDecimal profit = revenue.subtract(cost);
        BigDecimal margin = revenue.signum() == 0
                ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);

        return new SalesReportResponse(start, endExclusive.minusDays(1), revenue, cost, profit,
                margin, delivered, allOrders, statusCounts,
                new ArrayList<>(byMonth.values()), lost);
    }

    // ------------------------------------------------------------------ stock

    @Transactional(readOnly = true)
    public StockReportResponse stock() {
        List<StockReportResponse.ProductStock> rows = new ArrayList<>();
        BigDecimal purchaseValue = BigDecimal.ZERO;
        BigDecimal sellingValue = BigDecimal.ZERO;
        long lowStock = 0;
        long outOfStock = 0;

        for (Object[] row : reportRepository.stockByProduct()) {
            int onHand = ((Number) row[3]).intValue();
            BigDecimal purchasePrice = decimal(row, 4);
            BigDecimal sellingPrice = decimal(row, 5);
            int threshold = row[6] == null ? 5 : ((Number) row[6]).intValue();
            long unitsSold = ((Number) row[7]).longValue();

            BigDecimal rowPurchase = purchasePrice.multiply(BigDecimal.valueOf(onHand));
            BigDecimal rowSelling = sellingPrice.multiply(BigDecimal.valueOf(onHand));
            purchaseValue = purchaseValue.add(rowPurchase);
            sellingValue = sellingValue.add(rowSelling);

            boolean isLow = onHand > 0 && onHand <= threshold;
            if (isLow) lowStock++;
            if (onHand == 0) outOfStock++;

            rows.add(new StockReportResponse.ProductStock(
                    ((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                    onHand, purchasePrice, sellingPrice, threshold,
                    rowPurchase, rowSelling, rowSelling.subtract(rowPurchase),
                    unitsSold, isLow,
                    // Dead stock is sitting inventory that has never sold —
                    // an empty shelf isn't dead, it's just empty.
                    onHand > 0 && unitsSold == 0));
        }

        return new StockReportResponse(rows.size(), purchaseValue, sellingValue,
                sellingValue.subtract(purchaseValue), lowStock, outOfStock, rows);
    }

    // -------------------------------------------------------------- customers

    @Transactional(readOnly = true)
    public CustomerReportResponse customers(LocalDate from, LocalDate to) {
        LocalDate start = startOrDefault(from);
        LocalDate endExclusive = endOrDefault(to);

        List<CustomerReportResponse.CityTotal> byCity =
                reportRepository.ordersByCity(start.atStartOfDay(), endExclusive.atStartOfDay()).stream()
                        .map(row -> new CustomerReportResponse.CityTotal(
                                (String) row[0], ((Number) row[1]).longValue(), decimal(row, 2)))
                        .collect(Collectors.toList());

        List<CustomerReportResponse.CustomerTotal> customers =
                reportRepository.customerTotals(start.atStartOfDay(), endExclusive.atStartOfDay()).stream()
                        .map(row -> new CustomerReportResponse.CustomerTotal(
                                ((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                                (String) row[3], ((Number) row[4]).longValue(), decimal(row, 5)))
                        .collect(Collectors.toList());

        long returning = customers.stream().filter(c -> c.getOrders() > 1).count();
        BigDecimal totalSpend = customers.stream()
                .map(CustomerReportResponse.CustomerTotal::getSpend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = customers.isEmpty()
                ? BigDecimal.ZERO
                : totalSpend.divide(BigDecimal.valueOf(customers.size()), 2, RoundingMode.HALF_UP);

        return new CustomerReportResponse(start, endExclusive.minusDays(1),
                customers.size(), returning, average, byCity, customers);
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public UserReportResponse users(LocalDate from, LocalDate to) {
        LocalDate start = startOrDefault(from);
        LocalDate endExclusive = endOrDefault(to);

        // Commission settings live on the User row, so they're applied here
        // rather than in SQL — the shape differs per method.
        Map<Long, User> staff = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UserReportResponse.UserPerformance> rows =
                reportRepository.userPerformance(start.atStartOfDay(), endExclusive.atStartOfDay()).stream()
                        .map(row -> {
                            Long userId = ((Number) row[0]).longValue();
                            BigDecimal revenue = decimal(row, 7);
                            BigDecimal profit = decimal(row, 8);
                            long units = ((Number) row[9]).longValue();
                            return new UserReportResponse.UserPerformance(
                                    userId, (String) row[1], (String) row[2],
                                    ((Number) row[3]).longValue(), ((Number) row[4]).longValue(),
                                    ((Number) row[5]).longValue(), ((Number) row[6]).longValue(),
                                    revenue, profit, units,
                                    commissionFor(staff.get(userId), revenue, units));
                        })
                        .collect(Collectors.toList());

        return new UserReportResponse(start, endExclusive.minusDays(1), rows);
    }

    /** Mirrors the commission_shape rule: percentage of revenue, or a flat rate per unit. */
    private BigDecimal commissionFor(User user, BigDecimal revenue, long units) {
        if (user == null || !user.isCommissionEnabled() || user.getCommissionMethod() == null) {
            return BigDecimal.ZERO;
        }
        Integer minUnits = user.getCommissionMinUnits();
        if (minUnits != null && units < minUnits) {
            return BigDecimal.ZERO;
        }
        if (user.getCommissionMethod() == CommissionMethod.product_percentage) {
            BigDecimal percent = user.getCommissionPercent();
            return percent == null ? BigDecimal.ZERO
                    : revenue.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        BigDecimal perUnit = user.getCommissionUnitAmount();
        return perUnit == null ? BigDecimal.ZERO : perUnit.multiply(BigDecimal.valueOf(units));
    }

    // ---------------------------------------------------------------- finance

    @Transactional(readOnly = true)
    public FinanceReportResponse finance(LocalDate from, LocalDate to) {
        LocalDate start = startOrDefault(from);
        LocalDate endExclusive = endOrDefault(to);
        LocalDate lastMonth = endExclusive.minusMonths(1).withDayOfMonth(1);

        Map<LocalDate, FinanceReportResponse.MonthPoint> byMonth = new TreeMap<>();
        for (Object[] row : reportRepository.salesByMonth(start.atStartOfDay(), endExclusive.atStartOfDay())) {
            LocalDate month = localDate(row[0]);
            byMonth.put(month, new FinanceReportResponse.MonthPoint(
                    month, decimal(row, 1), decimal(row, 2),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        mergeManual(byMonth, FinanceKind.revenue, start, lastMonth);
        mergeManual(byMonth, FinanceKind.expense, start, lastMonth);

        BigDecimal tradingRevenue = BigDecimal.ZERO;
        BigDecimal tradingCost = BigDecimal.ZERO;
        BigDecimal manualRevenue = BigDecimal.ZERO;
        BigDecimal manualExpense = BigDecimal.ZERO;
        for (FinanceReportResponse.MonthPoint point : byMonth.values()) {
            point.setNet(point.getTradingRevenue()
                    .add(point.getManualRevenue())
                    .subtract(point.getTradingCost())
                    .subtract(point.getManualExpense()));
            tradingRevenue = tradingRevenue.add(point.getTradingRevenue());
            tradingCost = tradingCost.add(point.getTradingCost());
            manualRevenue = manualRevenue.add(point.getManualRevenue());
            manualExpense = manualExpense.add(point.getManualExpense());
        }

        BigDecimal net = tradingRevenue.add(manualRevenue).subtract(tradingCost).subtract(manualExpense);
        return new FinanceReportResponse(start, endExclusive.minusDays(1),
                tradingRevenue, tradingCost, manualRevenue, manualExpense, net,
                new ArrayList<>(byMonth.values()));
    }

    private void mergeManual(Map<LocalDate, FinanceReportResponse.MonthPoint> byMonth,
                             FinanceKind kind, LocalDate start, LocalDate lastMonth) {
        for (Object[] row : financeEntryRepository.monthlyTotals(kind, start, lastMonth)) {
            LocalDate month = (LocalDate) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            FinanceReportResponse.MonthPoint point = byMonth.computeIfAbsent(month,
                    m -> new FinanceReportResponse.MonthPoint(
                            m, BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            if (kind == FinanceKind.revenue) {
                point.setManualRevenue(amount);
            } else {
                point.setManualExpense(amount);
            }
        }
    }

    // -------------------------------------------------------------- dashboard

    @Transactional(readOnly = true)
    public DashboardSummaryResponse dashboard() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate nextMonth = thisMonth.plusMonths(1);
        LocalDate lastMonth = thisMonth.minusMonths(1);

        Object[] current = firstRow(reportRepository.salesTotals(
                thisMonth.atStartOfDay(), nextMonth.atStartOfDay()));
        Object[] previous = firstRow(reportRepository.salesTotals(
                lastMonth.atStartOfDay(), thisMonth.atStartOfDay()));

        BigDecimal revenueThisMonth = decimal(current, 0);
        BigDecimal revenueLastMonth = decimal(previous, 0);
        BigDecimal change = revenueLastMonth.signum() == 0
                ? null
                : revenueThisMonth.subtract(revenueLastMonth)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(revenueLastMonth, 1, RoundingMode.HALF_UP);

        Object[] alerts = firstRow(reportRepository.stockAlertCounts());

        LocalDate trendStart = thisMonth.minusMonths(DEFAULT_WINDOW_MONTHS - 1L);
        List<DashboardSummaryResponse.RevenuePoint> trend =
                reportRepository.salesByMonth(trendStart.atStartOfDay(), nextMonth.atStartOfDay()).stream()
                        .map(row -> new DashboardSummaryResponse.RevenuePoint(localDate(row[0]), decimal(row, 1)))
                        .collect(Collectors.toList());

        List<OrderResponse> recent = orderService
                .list(null, null, 0, RECENT_ORDER_COUNT)
                .getContent();

        long openOrders = OPEN_STATUSES.stream()
                .mapToLong(status -> orderRepository
                        .findByStatusOrderByOrderedAtDesc(status, PageRequest.of(0, 1))
                        .getTotalElements())
                .sum();

        return new DashboardSummaryResponse(
                revenueThisMonth, revenueLastMonth, change,
                number(current, 3),
                customerRepository.count(),
                productRepository.count(),
                number(alerts, 0),
                number(alerts, 1),
                openOrders,
                trend, recent);
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate startOrDefault(LocalDate from) {
        return (from != null ? from : LocalDate.now().minusMonths(DEFAULT_WINDOW_MONTHS - 1L)).withDayOfMonth(1);
    }

    /** Exclusive upper bound: the first day of the month after {@code to}. */
    private LocalDate endOrDefault(LocalDate to) {
        return (to != null ? to : LocalDate.now()).withDayOfMonth(1).plusMonths(1);
    }

    private Object[] firstRow(List<Object[]> rows) {
        return rows.isEmpty() ? new Object[0] : rows.get(0);
    }

    private BigDecimal decimal(Object[] row, int index) {
        if (row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        Object value = row[index];
        return value instanceof BigDecimal
                ? (BigDecimal) value
                : BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private long number(Object[] row, int index) {
        return row.length <= index || row[index] == null ? 0 : ((Number) row[index]).longValue();
    }

    /** Postgres returns a java.sql.Date for a ::date cast. */
    private LocalDate localDate(Object value) {
        return value instanceof Date ? ((Date) value).toLocalDate() : (LocalDate) value;
    }

    private LocalDateTime localDateTime(Object value) {
        return value instanceof Timestamp ? ((Timestamp) value).toLocalDateTime() : (LocalDateTime) value;
    }
}
