package com.knox.galaxy.model;

public enum BillingPlan {
    basic, nova, stellar;

    /**
     * Maps the KNOX client-manager plan (what staff pick in Galaxy-client) onto
     * the tenant-facing Galaxy tier stored in {@code knox.subscriptions}.
     * 2k tiers are Basic, 5k tiers are Nova, unlimited is Stellar; monthly vs
     * yearly is a billing cadence, not a feature tier.
     */
    public static BillingPlan fromKnoxPlan(KnoxPlan plan) {
        if (plan == null) {
            return basic;
        }
        switch (plan) {
            case monthly_5k:
            case yearly_5k:
                return nova;
            case unlimited:
                return stellar;
            case monthly_2k:
            case yearly_2k:
            default:
                return basic;
        }
    }
}
