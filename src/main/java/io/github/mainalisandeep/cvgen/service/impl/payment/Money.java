package io.github.mainalisandeep.cvgen.service.impl.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Paisa to and from the rupee strings gateways speak. Never through a double. */
final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    /** {@code 60000} to {@code "600"}, {@code 60050} to {@code "600.50"}. */
    static String rupees(long minor) {
        BigDecimal value = BigDecimal.valueOf(minor).divide(HUNDRED, 2, RoundingMode.UNNECESSARY);
        return value.stripTrailingZeros().scale() <= 0
                ? value.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                : value.toPlainString();
    }

    /** {@code "600.0"} or {@code 600} to {@code 60000}; unparseable is {@code -1}, which matches no order. */
    static long minor(String rupees) {
        try {
            return new BigDecimal(rupees.replace(",", "").strip()).multiply(HUNDRED)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
