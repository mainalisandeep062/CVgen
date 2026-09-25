package io.github.mainalisandeep.cvgen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.dto.CvCreateRequestDto;
import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.CreditTransactionType;
import io.github.mainalisandeep.cvgen.enums.CvTemplateLayout;
import io.github.mainalisandeep.cvgen.repository.CreditTransactionRepository;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.CvTemplateRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import io.github.mainalisandeep.cvgen.support.PostgresContainerSupport;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Premium templates: locked until paid for with credits, paid for once, and enforced wherever a
 * CV picks its template.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TemplateUnlockControllerTest extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CvRepository cvRepository;

    @Autowired
    private CvTemplateRepository templateRepository;

    @Autowired
    private CreditTransactionRepository transactionRepository;

    private User user;
    private CvTemplate premium;

    @BeforeEach
    void setUp() {
        cvRepository.deleteAll();
        user = userRepository.save(User.builder()
                .email("unlock-" + UUID.randomUUID() + "@test.com")
                .name("Unlocker")
                .emailVerified(true)
                .build());
        premium = templateRepository.save(CvTemplate.builder()
                .templateKey("executive-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Executive")
                .layout(CvTemplateLayout.CLASSIC.getKey())
                .accentColor("#0F766E")
                .supportedSections(CvTemplateLayout.CLASSIC.getSupportedSections())
                .premium(true)
                .creditCost(3)
                .sortOrder(50)
                .build());
    }

    @Test
    @DisplayName("The picker marks a premium template locked, free ones unlocked")
    void pickerShowsLockState() throws Exception {
        mockMvc.perform(get("/api/templates").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='" + premium.getTemplateKey() + "')].unlocked", contains(false)))
                .andExpect(jsonPath("$.data[?(@.key=='classic')].unlocked", contains(true)));
    }

    @Test
    @DisplayName("A locked premium template cannot be chosen for a CV")
    void lockedTemplateIsRefused() throws Exception {
        createCv(premium.getTemplateKey()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unlocking without enough credits is refused and charges nothing")
    void unlockNeedsCredits() throws Exception {
        unlock(premium.getTemplateKey()).andExpect(status().isBadRequest());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCreditBalance()).isZero();
    }

    @Test
    @DisplayName("Unlocking spends the cost once; the template is then usable")
    void unlockSpendsOnce() throws Exception {
        user.setCreditBalance(5);
        userRepository.saveAndFlush(user);

        unlock(premium.getTemplateKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.template.unlocked").value(true))
                .andExpect(jsonPath("$.data.balance").value(2));
        unlock(premium.getTemplateKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2));

        var spends = transactionRepository.findAll().stream()
                .filter(row -> row.getUser().getId().equals(user.getId()))
                .toList();
        assertThat(spends).hasSize(1);
        assertThat(spends.get(0).getType()).isEqualTo(CreditTransactionType.SPEND);
        assertThat(spends.get(0).getCredits()).isEqualTo(-3);
        assertThat(spends.get(0).getNote()).isEqualTo("Unlocked the \"Executive\" template");

        createCv(premium.getTemplateKey()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Unlocking a free template charges nothing")
    void freeTemplateIsFree() throws Exception {
        unlock("classic")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    private ResultActions unlock(String key) throws Exception {
        return mockMvc.perform(post("/api/templates/{key}/unlock", key).with(asUser(user)));
    }

    private ResultActions createCv(String templateKey) throws Exception {
        var request = CvCreateRequestDto.builder().title("Premium CV").templateKey(templateKey).build();
        return mockMvc.perform(post("/api/cvs")
                .with(asUser(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private RequestPostProcessor asUser(User account) {
        UserPrincipal principal = UserPrincipal.localUser(
                account.getId().toString(),
                account.getName(),
                account.getEmail(),
                account.getEmail(),
                account.getPasswordHash(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }
}
