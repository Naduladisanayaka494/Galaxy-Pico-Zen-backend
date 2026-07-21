package com.knox.galaxy.service;

import com.knox.galaxy.model.KnoxPlan;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Translates between the UI's period keys and {@code subscription_periods.period_start}.
 *
 * <pre>
 *   monthly / unlimited : "2026-04" &lt;-&gt; 2026-04-01
 *   yearly              : "2026"    &lt;-&gt; 2026-01-01
 * </pre>
 *
 * <p>Yearly periods are keyed by calendar year, not by the client's start
 * anniversary — that is what the Dashboard already does, and changing it would
 * silently re-bucket existing payments.
 */
public final class PeriodKeys {

    private PeriodKeys() {
    }

    public static boolean isYearly(KnoxPlan plan) {
        return plan == KnoxPlan.yearly_2k || plan == KnoxPlan.yearly_5k;
    }

    /** @throws IllegalArgumentException if the key does not match the plan's shape */
    public static LocalDate toPeriodStart(KnoxPlan plan, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Period key is required");
        }
        try {
            if (isYearly(plan)) {
                if (!key.matches("^\\d{4}$")) {
                    throw new IllegalArgumentException(
                            "Yearly plan expects a period key like '2026', got '" + key + "'");
                }
                return LocalDate.of(Integer.parseInt(key), 1, 1);
            }
            if (!key.matches("^\\d{4}-\\d{2}$")) {
                throw new IllegalArgumentException(
                        "Monthly plan expects a period key like '2026-04', got '" + key + "'");
            }
            return LocalDate.parse(key + "-01");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid period key: " + key, e);
        }
    }

    public static String toKey(KnoxPlan plan, LocalDate periodStart) {
        return isYearly(plan)
                ? String.valueOf(periodStart.getYear())
                : String.format("%04d-%02d", periodStart.getYear(), periodStart.getMonthValue());
    }
}
