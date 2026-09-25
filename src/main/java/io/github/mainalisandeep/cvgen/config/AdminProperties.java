package io.github.mainalisandeep.cvgen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * How the first administrator comes to exist.
 * <p>
 * Nothing in the API can create an admin out of nothing, and no migration seeds one, so every
 * environment names its own. Defaults to empty so the application and the test context boot
 * without configuration - and without an admin.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /**
     * Accounts promoted to ADMIN at startup and at every token issue, compared case-insensitively.
     * Only an email-verified account is promoted: an unverified local signup proves nothing about
     * who owns the address. Removing an address stops future promotion but does not demote anyone.
     */
    private List<String> bootstrapEmails = new ArrayList<>();
}
