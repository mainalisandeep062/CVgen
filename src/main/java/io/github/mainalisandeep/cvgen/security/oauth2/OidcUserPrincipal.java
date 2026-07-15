package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.security.IdentifiedPrincipal;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;
import java.util.Collection;
import java.util.Map;

public final class OidcUserPrincipal implements OidcUser, UserDetails, IdentifiedPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UserPrincipal delegate;
    private final OidcIdToken idToken;
    private final OidcUserInfo oidcUserInfo;

    public OidcUserPrincipal(UserPrincipal delegate, OidcIdToken idToken, OidcUserInfo oidcUserInfo) {
        this.delegate = delegate;
        this.idToken = idToken;
        this.oidcUserInfo = oidcUserInfo;
    }

    public UserPrincipal getDelegate() {
        return delegate;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public Map<String, Object> getClaims() {
        if (idToken != null) {
            return idToken.getClaims();
        }
        return delegate.getAttributes();
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUserInfo;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getPassword() {
        return delegate.getPassword();
    }

    @Override
    public String getUsername() {
        return delegate.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return delegate.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return delegate.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return delegate.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
