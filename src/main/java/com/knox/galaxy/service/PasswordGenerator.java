package com.knox.galaxy.service;

import java.security.SecureRandom;

/**
 * Generates one-time passwords for auto-provisioned tenant owners. Only ever
 * used at the moment of provisioning — the plaintext is emailed once and then
 * only the bcrypt hash is kept, same as any user-chosen password.
 */
public final class PasswordGenerator {

    private static final String CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
