package com.umar.ecommerce.payment.config;

import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.RefundScenario;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.simulator")
public class PaymentSimulatorProperties {

    private boolean controlEnabled;
    private String controlToken = "";
    private ChargeScenario chargeOutcome = ChargeScenario.SUCCESS;
    private RefundScenario refundOutcome = RefundScenario.SUCCESS;
    private Duration delay = Duration.ofSeconds(2);

    public boolean isControlEnabled() {
        return controlEnabled;
    }

    public void setControlEnabled(boolean controlEnabled) {
        this.controlEnabled = controlEnabled;
    }

    public String getControlToken() {
        return controlToken;
    }

    public void setControlToken(String controlToken) {
        this.controlToken = controlToken == null ? "" : controlToken;
    }

    public ChargeScenario getChargeOutcome() {
        return chargeOutcome;
    }

    public void setChargeOutcome(ChargeScenario chargeOutcome) {
        this.chargeOutcome = chargeOutcome;
    }

    public RefundScenario getRefundOutcome() {
        return refundOutcome;
    }

    public void setRefundOutcome(RefundScenario refundOutcome) {
        this.refundOutcome = refundOutcome;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }
}
