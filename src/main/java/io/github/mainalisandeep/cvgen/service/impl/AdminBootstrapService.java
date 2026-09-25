package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.config.AdminProperties;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.enums.UserRole;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the first administrators from {@code app.admin.bootstrap-emails}.
 * <p>
 * Runs twice: once at startup for accounts that already exist, and at every token issue for an account
 * that verifies its email later. Only verified accounts qualify - an unverified local signup proves
 * nothing about who owns the address, and promoting it would hand admin to whoever typed it first.
 * <p>
 * Promotion is one-way. Removing an address from the list stops future promotions but demotes nobody;
 * conversely an admin demoted in the UI is promoted again at their next login while their address is
 * still listed.
 */
@Service
@RequiredArgsConstructor
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> emails = bootstrapEmails();
        if (emails.isEmpty()) {
            return;
        }
        userRepository.findVerifiedByLowerEmailInAndRoleNot(emails, UserRole.ADMIN)
                .forEach(user -> promote(user, "startup"));
    }

    /**
     * Promotes {@code user} in place when listed. The caller persists it, and must call this before
     * building the principal so the promotion is already in the token being issued.
     */
    public void promoteIfListed(User user) {
        if (user.getRole() != UserRole.ADMIN
                && user.isEmailVerified()
                && bootstrapEmails().contains(normalise(user.getEmail()))) {
            promote(user, "token issue");
        }
    }

    private void promote(User user, String trigger) {
        user.setRole(UserRole.ADMIN);
        log.info("Promoted user {} ({}) to ADMIN from app.admin.bootstrap-emails at {}", user.getId(), user.getEmail(), trigger);
    }

    private Set<String> bootstrapEmails() {
        return adminProperties.getBootstrapEmails().stream()
                .filter(email -> email != null && !email.isBlank())
                .map(this::normalise)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
