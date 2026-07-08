package io.github.mainalisandeep.cvgen.security;

import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!StringUtils.hasText(username)) {
            throw new UsernameNotFoundException("Username must not be blank");
        }

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("No registered user found for username: " + username));

        return toUserPrincipal(user);
    }

    private UserPrincipal toUserPrincipal(User user) {
        return UserPrincipal.localUser(
                user.getId().toString(),
                user.getName(),
                user.getEmail(),
                user.getEmail(),
                user.getPasswordHash(),
                Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
