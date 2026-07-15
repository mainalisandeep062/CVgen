package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final OAuth2UserResolver oAuth2UserResolver;

    public CustomOidcUserService(OAuth2UserResolver oAuth2UserResolver) {
        this.oAuth2UserResolver = oAuth2UserResolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate to Spring's OIDC service for the actual handshake
        OidcUser oidcUser = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // Resolve or link user via shared resolver
        User user = oAuth2UserResolver.resolve(registrationId, oidcUser.getAttributes());

        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        String username = firstNonBlank(oidcUser.getEmail(), oidcUser.getFullName(), registrationId + ":" + oidcUser.getSubject());
        if (username == null) {
            username = user.getEmail();
        }

        UserPrincipal userPrincipal = UserPrincipal.oauth2User(
                user.getId().toString(),
                registrationId,
                oidcUser.getFullName(),
                username,
                user.getEmail(),
                oidcUser.getUserInfo() != null ? (String) oidcUser.getUserInfo().getClaims().get("picture") : null,
                oidcUser.getAttributes(),
                authorities
        );

        return new OidcUserPrincipal(userPrincipal, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
