package com.connectit.core.util;

/**
 * Replicates the JavaScript simpleHash used in the existing frontend/Node backend.
 * Preserved for backward-compatibility with existing stored password hashes.
 */
public final class SimpleHash {

    private SimpleHash() {}

    public static String hash(String value) {
        if (value == null) return null;
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            hash = ((hash << 5) - hash) + c;
            hash = hash & hash; // Convert to 32-bit int
        }
        return "h_" + Integer.toString(Math.abs(hash), 36) + "_" + value.length();
    }
}
