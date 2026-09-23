package com.ridhitek.backend.config;

/**
 * Test-only stand-in for the real backend's TenantContextHolder, which
 * ImageVerificationService.resolveTenantId() looks up reflectively at runtime
 * (it isn't a compile-time dependency of this plugin). Defaults to blank so the
 * com.uniquepeople.config fallback lookup is what actually resolves by default;
 * tests can flip {@code tenantId} to simulate this holder resolving directly,
 * and must restore it to "" afterward.
 * <p>
 * {@code throwOnAccess} lets tests simulate the reflective lookup blowing up
 * (e.g. a real-world classloader/version mismatch), which resolveTenantId()
 * swallows via {@code catch (Throwable t)}. Tests that set it must restore it
 * to {@code false} afterward.
 */
public class TenantContextHolder {
    public static String tenantId = "";
    public static boolean throwOnAccess = false;

    public static String getTenantId() {
        if (throwOnAccess) {
            throw new RuntimeException("simulated TenantContextHolder failure");
        }
        return tenantId;
    }
}