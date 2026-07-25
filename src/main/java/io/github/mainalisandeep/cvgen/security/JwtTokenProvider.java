package io.github.mainalisandeep.cvgen.security;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import io.github.mainalisandeep.cvgen.enums.ClaimType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    /** Lifetime of a refresh token; the refresh cookie is built with the same value. */
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    public static final String CLAIM_ID = "id";
    public static final String CLAIM_PROVIDER = "provider";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_IMAGE_URL = "imageUrl";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_TYPE = "type";

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecurityProperties securityProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.secretKey = buildSecretKey(securityProperties.getJwt().getSecret());
    }

    public String generateToken(UserPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Instant now = Instant.now();
        Instant expiration = now.plus(securityProperties.getJwt().getExpiration());
        return buildToken(principal, now, expiration, ClaimType.ACCESS.getValue());
    }

    public String generateRefreshToken(UserPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Instant now = Instant.now();
        Instant expiration = now.plus(REFRESH_TOKEN_TTL);
        return buildToken(principal, now, expiration, ClaimType.REFRESH.getValue());
    }

    private String buildToken(UserPrincipal principal, Instant issuedAt, Instant expiration, String type) {
        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(securityProperties.getJwt().getIssuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .claim(CLAIM_ID, principal.getId())
                .claim(CLAIM_PROVIDER, principal.getProvider())
                .claim(CLAIM_NAME, principal.getName())
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_IMAGE_URL, principal.getImageUrl())
                .claim(CLAIM_AUTHORITIES, principal.authorityNames())
                .claim(CLAIM_TYPE, type)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return validateTokenOfType(token, ClaimType.ACCESS.getValue());
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenOfType(token, ClaimType.REFRESH.getValue());
    }

    private boolean validateTokenOfType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (Exception ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** User id carried by the {@code id} claim of an access or refresh token. */
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).get(CLAIM_ID, String.class));
    }

    public Claims getClaims(String token) {
        return parseClaims(token);
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(extractAuthorities(claims));
        UserPrincipal principal = UserPrincipal.oauth2User(
                claims.get(CLAIM_ID, String.class),
                claims.get(CLAIM_PROVIDER, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.getSubject(),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_IMAGE_URL, String.class),
                claims,
                authorities
        );
        return UsernamePasswordAuthenticationToken.authenticated(principal, token, authorities);
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant().atZone(ZoneOffset.UTC).toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clockSkewSeconds(securityProperties.getJwt().getClockSkew().toSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Claims claims) {
        Object value = claims.get(CLAIM_AUTHORITIES);
        List<String> authorityNames = new ArrayList<>();

        if (value instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (entry != null) {
                    authorityNames.add(String.valueOf(entry));
                }
            }
        } else if (value instanceof String authorities) {
            for (String part : authorities.split(",")) {
                if (!part.isBlank()) {
                    authorityNames.add(part.trim());
                }
            }
        }

        if (authorityNames.isEmpty()) {
            authorityNames.add(securityProperties.getOauth2().getDefaultRole());
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String authorityName : authorityNames) {
            authorities.add(new SimpleGrantedAuthority(authorityName));
        }
        return authorities;
    }

    private SecretKey buildSecretKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build JWT signing key", ex);
        }
    }
}
