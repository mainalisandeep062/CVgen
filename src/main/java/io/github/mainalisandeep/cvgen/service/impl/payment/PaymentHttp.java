package io.github.mainalisandeep.cvgen.service.impl.payment;

import io.github.mainalisandeep.cvgen.config.PaymentProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** The one HTTP client gateways share: bounded timeouts, no redirects - a gateway API answers directly. */
@Component
public class PaymentHttp {

    private final RestClient client;

    public PaymentHttp(PaymentProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.getTimeout());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public RestClient client() {
        return client;
    }
}
