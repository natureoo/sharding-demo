package com.t20worldcup.states;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.TransactionUtilitiesKt;
import net.corda.core.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class EHKDType extends TokenType {

    public static final String IDENTIFIER = "EHKD";
    public static final int FRACTION_DIGITS = 2;

    @NotNull
    public static SecureHash getContractAttachment() {
        //noinspection ConstantConditions
        return TransactionUtilitiesKt.getAttachmentIdForGenericParam(new MealTicketType());
    }

    /**
     * This creates the {@link TokenType} for air-miles. Beware of the JAR hash issues.
     */
    public EHKDType() {
        super(IDENTIFIER, FRACTION_DIGITS);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash("EHKDType");
    }
}
