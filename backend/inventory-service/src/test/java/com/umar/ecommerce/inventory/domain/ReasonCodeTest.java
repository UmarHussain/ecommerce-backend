package com.umar.ecommerce.inventory.domain;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReasonCodeTest {

    @Test
    void setupAllowsZeroOpeningBalanceAndRejectsAdjustmentReasons() {
        ReasonCode.OPENING_BALANCE.requireForSetup(0);
        assertThatThrownBy(() -> ReasonCode.INBOUND_RECEIPT.requireForSetup(1))
                .isInstanceOf(InventoryProblem.class)
                .extracting(error -> ((InventoryProblem) error).code())
                .isEqualTo(InventoryProblem.VALIDATION_FAILED);
    }

    @Test
    void adjustmentSignsAreRestrictedAndZeroIsRejected() {
        ReasonCode.INBOUND_RECEIPT.requireForAdjustment(2);
        ReasonCode.RETURN.requireForAdjustment(1);
        ReasonCode.DAMAGE_LOSS.requireForAdjustment(-3);
        ReasonCode.CORRECTION.requireForAdjustment(-1);
        ReasonCode.CORRECTION.requireForAdjustment(4);
        assertThatThrownBy(() -> ReasonCode.DAMAGE_LOSS.requireForAdjustment(1))
                .isInstanceOf(InventoryProblem.class);
        assertThatThrownBy(() -> ReasonCode.CORRECTION.requireForAdjustment(0))
                .isInstanceOf(InventoryProblem.class);
        assertThatThrownBy(() -> ReasonCode.OPENING_BALANCE.requireForAdjustment(1))
                .isInstanceOf(InventoryProblem.class);
    }

    @Test
    void fingerprintChangesWhenThePayloadChanges() {
        UUID variant = UUID.randomUUID();
        String first = RequestFingerprint.setup(variant, 1, ReasonCode.OPENING_BALANCE, " note ", null);
        String same = RequestFingerprint.setup(variant, 1, ReasonCode.OPENING_BALANCE, "note", " ");
        String different = RequestFingerprint.setup(variant, 2, ReasonCode.OPENING_BALANCE, "note", null);
        assertThat(first).isEqualTo(same).hasSize(64);
        assertThat(different).isNotEqualTo(first);
    }
}
