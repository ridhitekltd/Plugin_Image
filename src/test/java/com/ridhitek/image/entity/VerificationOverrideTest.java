package com.ridhitek.image.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationOverrideTest {

    @Test
    void onOverrideSetsOverriddenAtTimestamp() throws Exception {
        VerificationOverride override = new VerificationOverride();
        Method onOverride = VerificationOverride.class.getDeclaredMethod("onOverride");
        onOverride.setAccessible(true);

        onOverride.invoke(override);

        assertThat(override.getOverriddenAt()).isNotNull();
    }
}
