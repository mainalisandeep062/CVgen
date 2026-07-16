package io.github.mainalisandeep.cvgen.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UserPrincipal implements UserDetails, OAuth2User, IdentifiedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String provider;
    private final String name;
    private final String username;
    private final String email;
    private final String imageUrl;
    private final String password;
    private final Set<GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    public UserPrincipal(
            String id,
            String provider,
            String name,
            String username,
            String email,
            String imageUrl,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            Map<String, Object> attributes
    ) {
        this.id = id;
        this.provider = provider;
        this.name = name;
        this.username = username != null ? username : email;
        this.email = email;
        this.imageUrl = imageUrl;
        this.password = password;
        this.authorities = Collections.unmodifiableSet(new HashSet<>(authorities == null ? Set.of() : Set.copyOf(authorities)));
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public static UserPrincipal oauth2User(
            String id,
            String provider,
            String name,
            String username,
            String email,
            String imageUrl,
            Map<String, Object> attributes,
            Collection<? extends GrantedAuthority> authorities
    ) {
        return new UserPrincipal(id, provider, name, username, email, imageUrl, "", authorities, attributes);
    }

    public static UserPrincipal localUser(
            String id,
            String name,
            String username,
            String email,
            String password,
            Collection<? extends GrantedAuthority> authorities
    ) {
        return new UserPrincipal(id, "local", name, username, email, null, password, authorities, Collections.emptyMap());
    }

    @Override
    public String getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username != null ? username : email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return id != null ? name : getUsername();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserPrincipal that)) {
            return false;
        }
        return Objects.equals(getUsername(), that.getUsername())
                && Objects.equals(provider, that.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername(), provider);
    }

    public Set<String> authorityNames() {
        Set<String> names = new HashSet<>();
        for (GrantedAuthority authority : authorities) {
            if (authority != null) {
                names.add(authority.getAuthority());
            }
        }
        return Collections.unmodifiableSet(names);
    }

    public Set<GrantedAuthority> mutableAuthorities() {
        Set<GrantedAuthority> result = new HashSet<>();
        for (String authority : authorityNames()) {
            result.add(new SimpleGrantedAuthority(authority));
        }
        return result;
    }
}
