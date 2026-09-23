package com.ridhitek.backend.config;

/**
 * Test-only stand-in for the real backend's TenantContextHolder, which
 * ImageVerificationService.resolveTenantId() looks up reflectively at runtime
 * (it isn't a compile-time dependency of this plugin). Defaults to blank so the
 * com.uniquepeople.config fallback lookup is what actually resolves by default;
 * tests can flip {@code tenantId} to simulate this holder resolving directly,
 * and must restore it to "" afterward.
 */
public class TenantContextHolder {
    public static String tenantId = "";

    public static String getTenantId() {
        return tenantId;
    }
}
