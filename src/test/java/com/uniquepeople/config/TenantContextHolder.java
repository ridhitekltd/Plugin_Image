package com.uniquepeople.config;

/**
 * Test-only stand-in for the uniquepeople backend's TenantContextHolder, which
 * ImageVerificationService.resolveTenantId() looks up reflectively as a fallback
 * when the ridhitek.backend holder isn't present or returns blank. Defaults to a
 * resolvable value; tests that need resolveTenantId() to fail entirely (both
 * holders blank) must set this to "" too, and restore it to "resolved-tenant"
 * afterward so other tests aren't affected.
 */
public class TenantContextHolder {
    public static String tenantId = "resolved-tenant";

    public static String getTenantId() {
        return tenantId;
    }
}
