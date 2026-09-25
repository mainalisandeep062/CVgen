package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.dto.CreditPackRequestDto;
import io.github.mainalisandeep.cvgen.dto.RefundRequestDto;
import io.github.mainalisandeep.cvgen.entity.CreditPack;
import io.github.mainalisandeep.cvgen.entity.CreditTransaction;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionStatus;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.repository.CreditPackRepository;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.support.AdminTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Credit packs, the ledger and refunds.
 *
 * <p>Purchases are written straight to the repository: there is no payment gateway yet, so nothing in
 * the API can create one. That is exactly the row a refund needs.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AdminBillingControllerTest extends AdminTestSupport {

    @Autowired
    private CreditPackRepository creditPackRepository;

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        admin = saveAdmin("billing-admin");
        member = saveUser("billing-member");
    }

    // --- Packs ---

    @Test
    @DisplayName("The seeded packs are on sale, highlighted one included")
    void seededPacksAreListed() throws Exception {
        mockMvc.perform(get("/api/admin/billing/packs").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Starter"))
                .andExpect(jsonPath("$.data[0].credits").value(5))
                .andExpect(jsonPath("$.data[0].priceMinor").value(25000))
                .andExpect(jsonPath("$.data[0].currency").value("NPR"))
                .andExpect(jsonPath("$.data[1].name").value("Popular"))
                .andExpect(jsonPath("$.data[1].highlighted").value(true))
                .andExpect(jsonPath("$.data[2].name").value("Pro"));
    }

    @Test
    @DisplayName("A user sees only active packs, without purchase counts")
    void userSeesActivePacksOnly() throws Exception {
        UUID packId = createPack("Hidden", 3, 9000);
        deactivatePack(packId, "Hidden", 3, 9000);

        mockMvc.perform(get("/api/billing/packs").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='Hidden')]").isEmpty())
                .andExpect(jsonPath("$.data[0].purchases").value(0));
    }

    @Test
    @DisplayName("Pack create, update and delete round-trip")
    void packCrud() throws Exception {
        UUID packId = createPack("Test Pack", 7, 30000);

        var update = CreditPackRequestDto.builder()
                .name("Test Pack v2").credits(9).priceMinor(31000L).highlighted(true).sortOrder(9).build();

        mockMvc.perform(put("/api/admin/billing/packs/{packId}", packId)
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Pack v2"))
                .andExpect(jsonPath("$.data.credits").value(9))
                .andExpect(jsonPath("$.data.highlighted").value(true));

        mockMvc.perform(delete("/api/admin/billing/packs/{packId}", packId).with(as(admin)))
                .andExpect(status().isOk());

        assertThat(creditPackRepository.findById(packId)).isEmpty();
    }

    @Test
    @DisplayName("Invalid pack values fail validation")
    void invalidPackIsRejected() throws Exception {
        var request = CreditPackRequestDto.builder().name("").credits(0).priceMinor(-1L).build();

        mockMvc.perform(post("/api/admin/billing/packs")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("A pack with transactions cannot be deleted")
    void deletePackInUseConflicts() throws Exception {
        UUID packId = createPack("Sold", 5, 25000);
        purchase(creditPackRepository.findById(packId).orElseThrow());

        mockMvc.perform(delete("/api/admin/billing/packs/{packId}", packId).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false));

        assertThat(creditPackRepository.findById(packId)).isPresent();
    }

    // --- Transactions and refunds ---

    @Test
    @DisplayName("The ledger lists a purchase and can be filtered by type")
    void listTransactions() throws Exception {
        purchase(seededPack());

        mockMvc.perform(get("/api/admin/billing/transactions")
                        .param("type", CreditTransactionType.PURCHASE.name())
                        .param("q", member.getEmail())
                        .with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].userEmail").value(member.getEmail()))
                .andExpect(jsonPath("$.data.items[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$.data.items[0].packName").value("Popular"))
                .andExpect(jsonPath("$.data.items[0].amountMinor").value(60000));
    }

    @Test
    @DisplayName("A refund reverses the credits and marks the purchase refunded")
    void refundPurchase() throws Exception {
        CreditTransaction purchase = purchase(seededPack());

        mockMvc.perform(post("/api/admin/billing/transactions/{id}/refund", purchase.getId())
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RefundRequestDto.builder().note("Duplicate charge").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("REFUND"))
                .andExpect(jsonPath("$.data.credits").value(-15))
                .andExpect(jsonPath("$.data.balanceAfter").value(0))
                .andExpect(jsonPath("$.data.amountMinor").value(60000))
                .andExpect(jsonPath("$.data.note").value("Duplicate charge"));

        assertThat(creditTransactionRepository.findById(purchase.getId()).orElseThrow().getStatus())
                .isEqualTo(CreditTransactionStatus.REFUNDED);
        assertThat(userRepository.findById(member.getId()).orElseThrow().getCreditBalance()).isZero();
    }

    @Test
    @DisplayName("The same purchase cannot be refunded twice")
    void secondRefundConflicts() throws Exception {
        CreditTransaction purchase = purchase(seededPack());

        mockMvc.perform(post("/api/admin/billing/transactions/{id}/refund", purchase.getId()).with(as(admin)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/billing/transactions/{id}/refund", purchase.getId()).with(as(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false));
    }

    @Test
    @DisplayName("Refunding a transaction that is not a completed purchase conflicts")
    void refundOfNonPurchaseConflicts() throws Exception {
        CreditTransaction grant = creditTransactionRepository.save(CreditTransaction.builder()
                .user(member)
                .type(CreditTransactionType.ADMIN_GRANT)
                .status(CreditTransactionStatus.COMPLETED)
                .credits(5)
                .balanceAfter(5)
                .build());

        mockMvc.perform(post("/api/admin/billing/transactions/{id}/refund", grant.getId()).with(as(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Refunding credits the user has already spent is refused")
    void refundBelowZeroIsRejected() throws Exception {
        CreditTransaction purchase = purchase(seededPack());
        member.setCreditBalance(2);
        userRepository.save(member);

        mockMvc.perform(post("/api/admin/billing/transactions/{id}/refund", purchase.getId()).with(as(admin)))
                .andExpect(status().isBadRequest());

        assertThat(creditTransactionRepository.findById(purchase.getId()).orElseThrow().getStatus())
                .isEqualTo(CreditTransactionStatus.COMPLETED);
    }

    // --- Summary ---

    @Test
    @DisplayName("The summary reports the window's revenue and a zero-filled day series")
    void summaryShape() throws Exception {
        purchase(seededPack());

        mockMvc.perform(get("/api/admin/billing/summary").param("days", "7").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.currency").value("NPR"))
                .andExpect(jsonPath("$.data.revenueMinor").value(60000))
                .andExpect(jsonPath("$.data.purchases").value(1))
                .andExpect(jsonPath("$.data.averageOrderMinor").value(60000))
                .andExpect(jsonPath("$.data.creditsSold").value(15))
                .andExpect(jsonPath("$.data.refundsMinor").value(0))
                .andExpect(jsonPath("$.data.series.length()").value(7))
                .andExpect(jsonPath("$.data.series[6].revenueMinor").value(60000))
                .andExpect(jsonPath("$.data.topPacks[0].name").value("Popular"))
                .andExpect(jsonPath("$.data.paymentMethods[0].method").value("esewa"));
    }

    @Test
    @DisplayName("A window outside 7..365 days is rejected")
    void summaryRejectsOutOfRangeWindow() throws Exception {
        mockMvc.perform(get("/api/admin/billing/summary").param("days", "3").with(as(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A user sees their own balance and ledger")
    void userSeesOwnBillingAccount() throws Exception {
        purchase(seededPack());

        mockMvc.perform(get("/api/billing/me").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(15))
                .andExpect(jsonPath("$.data.currency").value("NPR"))
                .andExpect(jsonPath("$.data.transactions[0].type").value("PURCHASE"));
    }

    // --- Helpers ---

    private CreditPack seededPack() {
        return creditPackRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(pack -> "Popular".equals(pack.getName()))
                .findFirst()
                .orElseThrow();
    }

    /** A completed purchase, written the way the payment flow eventually will. */
    private CreditTransaction purchase(CreditPack pack) {
        member.setCreditBalance(member.getCreditBalance() + pack.getCredits());
        userRepository.save(member);
        return creditTransactionRepository.save(CreditTransaction.builder()
                .user(member)
                .type(CreditTransactionType.PURCHASE)
                .status(CreditTransactionStatus.COMPLETED)
                .credits(pack.getCredits())
                .balanceAfter(member.getCreditBalance())
                .amountMinor(pack.getPriceMinor())
                .pack(pack)
                .paymentMethod("esewa")
                .reference("test-" + UUID.randomUUID())
                .build());
    }

    private UUID createPack(String name, int credits, long priceMinor) throws Exception {
        var request = CreditPackRequestDto.builder().name(name).credits(credits).priceMinor(priceMinor).build();
        String body = mockMvc.perform(post("/api/admin/billing/packs")
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return UUID.fromString(data.path("id").asText());
    }

    private void deactivatePack(UUID packId, String name, int credits, long priceMinor) throws Exception {
        var request = CreditPackRequestDto.builder()
                .name(name).credits(credits).priceMinor(priceMinor).active(false).build();
        mockMvc.perform(put("/api/admin/billing/packs/{packId}", packId)
                        .with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
