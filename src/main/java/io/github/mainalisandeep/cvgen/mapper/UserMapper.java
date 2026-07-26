package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.config.SecurityProperties;
import io.github.mainalisandeep.cvgen.dto.UserResponseDto;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single place where a {@link User} entity is translated for the layers above it.
 */
@Component
@RequiredArgsConstructor
public class UserMapper {

    private final SecurityProperties securityProperties;

    /** Authenticated principal for a locally stored user. */
    public UserPrincipal toPrincipal(User user) {
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority(securityProperties.getOauth2().getDefaultRole()));
        return UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                authorities
        );
    }

    public UserResponseDto toResponseDto(User user, List<UserIdentity> identities) {
        return UserResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .createdAt(LocalDateTime.ofInstant(user.getCreatedAt(), ZoneId.systemDefault()))
                .isEmailVerified(user.isEmailVerified())
                .providers(identities.stream().map(UserIdentity::getProvider).toList())
                .build();
    }
}
