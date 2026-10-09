package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.config.PaymentSimulatorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorAccessTest {

    @Test
    void controlMustBeEnabledAndTheTokenMustMatch() {
        PaymentSimulatorProperties properties = new PaymentSimulatorProperties();
        properties.setControlToken("secret");
        properties.setControlEnabled(false);
        SimulatorAccess access = new SimulatorAccess(properties);
        assertThat(access.permitted("secret")).isFalse();

        properties.setControlEnabled(true);
        assertThat(access.permitted("secret")).isTrue();
        assertThat(access.permitted("other")).isFalse();
        assertThat(access.permitted(null)).isFalse();
        assertThat(access.permitted("")).isFalse();

        properties.setControlToken("");
        assertThat(access.permitted("")).isFalse();
        assertThat(access.permitted("secret")).isFalse();
    }
}
