package io.github.mainalisandeep.cvgen.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.config.constants.MatchersConfig;
import io.github.mainalisandeep.cvgen.enums.UserStatus;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Refuses every authenticated request from a suspended or deleted account, whatever token it carries.
 * <p>
 * An access token stays valid for its whole lifetime and cannot be recalled, so checking status only
 * when tokens are issued would let a suspended user keep working until it expires. This filter runs
 * after {@link JwtAuthFilter} and reads {@code users.status} for the authenticated principal.
 * <p>
 * Cost: one primary-key lookup of a single column per authenticated request. That is the price of
 * suspension taking effect immediately without a token denylist. If it ever shows in latency, cache the
 * status briefly and evict on change - do not drop the check.
 * <p>
 * On paths that do not require authentication (public and swagger matchers) a stale principal is
 * dropped rather than refused: a deleted user whose client still sends an old bearer token must be able
 * to reach signup and login. Login, OTP verify, OAuth exchange and refresh enforce suspension themselves.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CustomMessageSource customMessageSource;
    private final List<RequestMatcher> unauthenticatedMatchers;

    public AccountStatusFilter(UserRepository userRepository, ObjectMapper objectMapper, CustomMessageSource customMessageSource) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.customMessageSource = customMessageSource;
        this.unauthenticatedMatchers = Stream.concat(
                        MatchersConfig.PUBLIC_MATCHERS.stream(),
                        MatchersConfig.SWAGGER_MATCHERS.stream())
                .<RequestMatcher>map(AntPathRequestMatcher::new)
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID userId = authenticatedUserId();

        if (userId == null || userRepository.findStatusById(userId).filter(UserStatus.ACTIVE::equals).isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (unauthenticatedMatchers.stream().anyMatch(matcher -> matcher.matches(request))) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), GlobalApiResponse.builder()
                .status(false)
                .message(customMessageSource.get(ErrorConstantValue.ACCOUNT_SUSPENDED))
                .build());
    }

    private UUID authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication.getPrincipal() instanceof IdentifiedPrincipal principal && principal.getId() != null) {
            return UUID.fromString(principal.getId());
        }
        return null;
    }
}
