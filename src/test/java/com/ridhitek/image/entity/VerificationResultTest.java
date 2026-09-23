package com.ridhitek.image.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationResultTest {

    @Test
    void onCreateSetsCreatedAndUpdatedTimestamps() throws Exception {
        VerificationResult result = new VerificationResult();
        Method onCreate = VerificationResult.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);

        onCreate.invoke(result);

        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void onUpdateRefreshesUpdatedTimestamp() throws Exception {
        VerificationResult result = new VerificationResult();
        Method onUpdate = VerificationResult.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        onUpdate.invoke(result);

        assertThat(result.getUpdatedAt()).isNotNull();
    }
}
