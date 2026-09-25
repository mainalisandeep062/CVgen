package io.github.mainalisandeep.cvgen.service.impl.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EsewaGatewayClientTest {

    @Test
    @DisplayName("Signature matches the signed form in eSewa's ePay v2 documentation")
    void signatureMatchesPublishedVector() {
        // From the documented form (total 110, uuid 241028). The standalone HMAC example on the same
        // page shows a result that does not match its own input, so it is not used here.
        assertThat(EsewaGatewayClient.sign("total_amount=110,transaction_uuid=241028,product_code=EPAYTEST",
                "8gBm/:&EnhH.1/q"))
                .isEqualTo("i94zsd3oXF6ZsSr/kGqT4sSzYQzjj1W/waxjWyRwaME=");
    }

    @Test
    @DisplayName("Paisa become the rupee strings eSewa signs, and back")
    void convertsMoney() {
        assertThat(Money.rupees(60000)).isEqualTo("600");
        assertThat(Money.rupees(60050)).isEqualTo("600.50");
        assertThat(Money.minor("600.0")).isEqualTo(60000);
        assertThat(Money.minor("1,800")).isEqualTo(180000);
        assertThat(Money.minor("abc")).isEqualTo(-1);
    }
}
