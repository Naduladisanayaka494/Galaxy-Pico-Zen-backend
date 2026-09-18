package com.knox.galaxy.model;

/**
 * How finely a delivery method prices by region.
 *
 * <p>Doubles as the region kind on {@link DeliveryMethodRate}, where
 * {@code flat} is never valid — see the CHECK in V3__delivery_pricing.sql.
 */
public enum DeliveryRegionType {
    flat, province, district
}
