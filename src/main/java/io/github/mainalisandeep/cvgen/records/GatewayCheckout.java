package io.github.mainalisandeep.cvgen.records;

import java.util.Map;

/**
 * How the browser gets to a gateway's payment page.
 *
 * @param method {@code FORM_POST}: submit {@code fields} to {@code url} as a form (eSewa);
 *               {@code REDIRECT}: navigate to {@code url} (Khalti)
 * @param reference gateway-side id known at initiation, or {@code null}
 */
public record GatewayCheckout(String method, String url, Map<String, String> fields, String reference) {

    public static final String FORM_POST = "FORM_POST";
    public static final String REDIRECT = "REDIRECT";
}
