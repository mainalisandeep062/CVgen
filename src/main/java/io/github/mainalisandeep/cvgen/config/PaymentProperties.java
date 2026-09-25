package io.github.mainalisandeep.cvgen.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Payment gateways. A gateway with a blank secret is switched off and never offered at checkout,
 * so the application boots and runs without any of them configured.
 * <p>
 * The defaults point at the gateways' sandboxes. Going live is a configuration change: the live
 * URLs, the merchant's product code and secret, nothing in code.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.payments")
public class PaymentProperties {

    /**
     * Frontend origin the gateways send the browser back to, e.g. {@code https://cvgen.io}. The
     * return pages live under {@code /billing/return/<gateway>} there.
     */
    @NotBlank
    private String returnBaseUrl = "http://localhost:3000";

    /** Connect and read timeout for every call to a gateway. */
    @NotNull
    private Duration timeout = Duration.ofSeconds(10);

    @Valid
    private Esewa esewa = new Esewa();

    @Valid
    private Khalti khalti = new Khalti();

    /** eSewa ePay v2: the browser posts a signed form; completion is confirmed by the status API. */
    @Data
    public static class Esewa {

        @NotBlank
        private String formUrl = "https://rc-epay.esewa.com.np/api/epay/main/v2/form";

        @NotBlank
        private String statusUrl = "https://rc.esewa.com.np/api/epay/transaction/status/";

        @NotBlank
        private String productCode = "EPAYTEST";

        /** HMAC-SHA256 key for the form signature. Blank switches eSewa off. */
        private String secretKey = "";

        public boolean isEnabled() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    /** Khalti KPG-2: the server initiates, the browser is redirected, completion is confirmed by lookup. */
    @Data
    public static class Khalti {

        @NotBlank
        private String baseUrl = "https://dev.khalti.com/api/v2";

        /** Merchant live/test secret key, sent as {@code Authorization: Key <secret>}. Blank switches Khalti off. */
        private String secretKey = "";

        public boolean isEnabled() {
            return secretKey != null && !secretKey.isBlank();
        }
    }
}
