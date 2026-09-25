package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.mainalisandeep.cvgen.dto.CheckoutRequestDto;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.enums.PaymentGateway;
import io.github.mainalisandeep.cvgen.enums.PaymentOrderStatus;
import io.github.mainalisandeep.cvgen.repository.CreditPackRepository;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.PaymentOrderRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout and confirmation against a local stand-in for eSewa and Khalti.
 * <p>
 * Not {@code @Transactional}: the service commits its own short transactions around each gateway
 * call, which is the behaviour under test, so rows are cleaned up by hand instead.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentControllerTest extends PostgresContainerSupport {

    private static final String ESEWA_SECRET = "8gBm/:&EnhH.1/q";

    /** What the fake eSewa status API answers, per transaction_uuid. */
    private static final Map<String, String> ESEWA_STATUS = new ConcurrentHashMap<>();

    /** What the fake Khalti lookup answers, per pidx. */
    private static final Map<String, String> KHALTI_LOOKUP = new ConcurrentHashMap<>();

    private static HttpServer gateway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditPackRepository packRepository;

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private CreditTransactionRepository transactionRepository;

    private User buyer;
    private User stranger;
    private CreditPack pack;

    @BeforeAll
    static void startGateway() throws IOException {
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/esewa/status", exchange -> {
            String uuid = query(exchange, "transaction_uuid");
            respond(exchange, 200, ESEWA_STATUS.getOrDefault(uuid,
                    "{\"status\":\"NOT_FOUND\",\"ref_id\":null,\"total_amount\":0}"));
        });
        gateway.createContext("/khalti/epayment/initiate/", exchange -> {
            JsonNode body = new ObjectMapper().readTree(exchange.getRequestBody());
            String pidx = "pidx-" + body.path("purchase_order_id").asText();
            boolean authorised = "Key test-khalti-secret".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, authorised ? 200 : 401, "{\"pidx\":\"" + pidx + "\",\"payment_url\":\"https://test-pay.khalti.com/?pidx="
                    + pidx + "\",\"expires_at\":\"2026-09-25T12:00:00+05:45\"}");
        });
        gateway.createContext("/khalti/epayment/lookup/", exchange -> {
            JsonNode body = new ObjectMapper().readTree(exchange.getRequestBody());
            respond(exchange, 200, KHALTI_LOOKUP.getOrDefault(body.path("pidx").asText(),
                    "{\"status\":\"Pending\",\"total_amount\":0}"));
        });
        gateway.start();
    }

    @AfterAll
    static void stopGateway() {
        gateway.stop(0);
    }

    @DynamicPropertySource
    static void gateways(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + gateway.getAddress().getPort();
        registry.add("app.payments.esewa.secret-key", () -> ESEWA_SECRET);
        registry.add("app.payments.esewa.status-url", () -> base + "/esewa/status");
        registry.add("app.payments.khalti.secret-key", () -> "test-khalti-secret");
        registry.add("app.payments.khalti.base-url", () -> base + "/khalti");
        registry.add("app.payments.return-base-url", () -> "http://localhost:3000");
    }

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder()
                .email("buyer-" + UUID.randomUUID() + "@test.com")
                .name("Buyer")
                .emailVerified(true)
                .build());
        stranger = userRepository.save(User.builder()
                .email("stranger-" + UUID.randomUUID() + "@test.com")
                .name("Stranger")
                .emailVerified(true)
                .build());
        pack = packRepository.save(CreditPack.builder()
                .name("Test pack " + UUID.randomUUID())
                .credits(15)
                .priceMinor(60000)
                .sortOrder(99)
                .build());
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll(orderRepository.findAll().stream()
                .filter(order -> order.getPack() != null && order.getPack().getId().equals(pack.getId()))
                .toList());
        transactionRepository.deleteAll(transactionRepository.findAll().stream()
                .filter(row -> row.getUser().getId().equals(buyer.getId()))
                .toList());
        userRepository.deleteAll(List.of(buyer, stranger));
        packRepository.delete(pack);
        ESEWA_STATUS.clear();
        KHALTI_LOOKUP.clear();
    }

    @Test
    @DisplayName("Both configured gateways are offered")
    void listsGateways() throws Exception {
        mockMvc.perform(get("/api/billing/gateways").with(asUser(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", contains("ESEWA", "KHALTI")));
    }

    @Test
    @DisplayName("eSewa checkout is a signed form for the pack's price, and opens a pending order")
    void esewaCheckoutIsSignedForm() throws Exception {
        JsonNode data = checkout(buyer, PaymentGateway.ESEWA).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString().transform(this::data);

        String orderId = data.path("orderId").asText();
        JsonNode fields = data.path("fields");
        assertThat(data.path("method").asText()).isEqualTo("FORM_POST");
        assertThat(data.path("url").asText()).isEqualTo("https://rc-epay.esewa.com.np/api/epay/main/v2/form");
        assertThat(fields.path("total_amount").asText()).isEqualTo("600");
        assertThat(fields.path("transaction_uuid").asText()).isEqualTo(orderId);
        assertThat(fields.path("success_url").asText()).isEqualTo("http://localhost:3000/billing/return/esewa");
        assertThat(fields.path("signature").asText()).isEqualTo(
                io.github.mainalisandeep.cvgen.service.impl.payment.EsewaSignatureProbe.sign(
                        "total_amount=600,transaction_uuid=" + orderId + ",product_code=EPAYTEST", ESEWA_SECRET));
        assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getStatus())
                .isEqualTo(PaymentOrderStatus.PENDING);
    }

    @Test
    @DisplayName("A completed eSewa payment credits once, however often it is confirmed")
    void esewaConfirmCreditsOnce() throws Exception {
        String orderId = orderId(checkout(buyer, PaymentGateway.ESEWA));
        ESEWA_STATUS.put(orderId, "{\"status\":\"COMPLETE\",\"ref_id\":\"0007ZX9\",\"total_amount\":600.0}");

        confirm(buyer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.balance").value(15));
        confirm(buyer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(15));

        assertThat(userRepository.findById(buyer.getId()).orElseThrow().getCreditBalance()).isEqualTo(15);
        var purchases = transactionRepository.findAll().stream()
                .filter(row -> row.getUser().getId().equals(buyer.getId()))
                .toList();
        assertThat(purchases).hasSize(1);
        assertThat(purchases.get(0).getType()).isEqualTo(CreditTransactionType.PURCHASE);
        assertThat(purchases.get(0).getPaymentMethod()).isEqualTo("ESEWA");
        assertThat(purchases.get(0).getReference()).isEqualTo("0007ZX9");
        assertThat(purchases.get(0).getAmountMinor()).isEqualTo(60000);
    }

    @Test
    @DisplayName("Leaving eSewa without paying cancels the order and credits nothing")
    void esewaNotFoundCancels() throws Exception {
        String orderId = orderId(checkout(buyer, PaymentGateway.ESEWA));

        confirm(buyer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("A payment for a different amount than the order is never credited")
    void amountMismatchFails() throws Exception {
        String orderId = orderId(checkout(buyer, PaymentGateway.ESEWA));
        ESEWA_STATUS.put(orderId, "{\"status\":\"COMPLETE\",\"ref_id\":\"X1\",\"total_amount\":1.0}");

        confirm(buyer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("Khalti checkout redirects to the payment URL; lookup Completed credits the pack")
    void khaltiFlow() throws Exception {
        JsonNode data = checkout(buyer, PaymentGateway.KHALTI).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString().transform(this::data);
        String orderId = data.path("orderId").asText();

        assertThat(data.path("method").asText()).isEqualTo("REDIRECT");
        assertThat(data.path("url").asText()).startsWith("https://test-pay.khalti.com/?pidx=");
        assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getGatewayReference())
                .isEqualTo("pidx-" + orderId);

        confirm(buyer, orderId).andExpect(jsonPath("$.data.status").value("PENDING"));

        KHALTI_LOOKUP.put("pidx-" + orderId,
                "{\"status\":\"Completed\",\"transaction_id\":\"KT-1\",\"total_amount\":60000}");
        confirm(buyer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.balance").value(15));
    }

    @Test
    @DisplayName("Someone else's order is 404 to confirm and to read")
    void foreignOrderIsNotFound() throws Exception {
        String orderId = orderId(checkout(buyer, PaymentGateway.ESEWA));

        confirm(stranger, orderId).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/billing/orders/{id}", orderId).with(asUser(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A pack that is off sale cannot be bought")
    void inactivePackIsRefused() throws Exception {
        pack.setActive(false);
        packRepository.save(pack);

        checkout(buyer, PaymentGateway.ESEWA).andExpect(status().isBadRequest());
    }

    private ResultActions checkout(User user, PaymentGateway method) throws Exception {
        var request = CheckoutRequestDto.builder().packId(pack.getId()).gateway(method).build();
        return mockMvc.perform(post("/api/billing/checkout")
                .with(asUser(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions confirm(User user, String orderId) throws Exception {
        return mockMvc.perform(post("/api/billing/orders/{id}/confirm", orderId).with(asUser(user)));
    }

    private String orderId(ResultActions checkout) throws Exception {
        return data(checkout.andReturn().getResponse().getContentAsString()).path("orderId").asText();
    }

    private JsonNode data(String body) {
        try {
            return objectMapper.readTree(body).path("data");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name) && parts.length == 2) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private RequestPostProcessor asUser(User user) {
        UserPrincipal principal = UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
}
