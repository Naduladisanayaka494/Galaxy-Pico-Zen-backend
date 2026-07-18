package com.knox.galaxy.dto;

import com.knox.galaxy.model.KnoxPlan;
import com.knox.galaxy.model.KnoxSetupOption;
import com.knox.galaxy.model.KnoxStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Shaped for the Client Manager dashboard: setup payments as an ordered list,
 * subscription payments keyed the way the UI keys them.
 */
@Data
public class ClientResponse {
    private Long id;
    private String businessName;
    private String contactPerson;
    private String phone;
    private String email;
    private KnoxPlan plan;
    private LocalDate startDate;
    private KnoxSetupOption setupOption;
    private boolean onTrial;
    private boolean blocked;
    private String notes;

    /**
     * Effective status, computed server-side rather than in the browser: a trial
     * lapses on elapsed days, and the client's clock is not authoritative.
     */
    private KnoxStatus status;
    private int trialDaysLeft;

    /** Ordered by installment_no. One entry for full pay, four for installments. */
    private List<Boolean> setupPaid;

    /** period key -> paid */
    private Map<String, Boolean> subPayments;

    /** period key -> amount. Absent means "not entered" (per-order plans). */
    private Map<String, BigDecimal> subAmounts;

    /** Null if this client has no provisioned Galaxy tenant. */
    private String tenantSlug;

    /**
     * Only meaningful on the response to create(): whether the welcome email
     * actually sent. False + a non-null temporaryPassword means the tenant
     * was provisioned successfully but the email failed — staff need to
     * relay the password some other way.
     */
    private Boolean emailSent;
    private String temporaryPassword;
}
