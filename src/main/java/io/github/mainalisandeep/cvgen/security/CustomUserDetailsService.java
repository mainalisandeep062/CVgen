package io.github.mainalisandeep.cvgen.security;

import io.github.mainalisandeep.cvgen.mapper.UserMapper;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!StringUtils.hasText(username)) {
            throw new UsernameNotFoundException("Username must not be blank");
        }

        return userRepository.findByEmail(username)
                .map(userMapper::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("No registered user found for username: " + username));
    }
}
