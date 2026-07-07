package io.github.mainalisandeep.cvgen.security;

import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2ExchangeCodeStore;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final OAuth2ExchangeCodeStore exchangeCodeStore;

	public CustomUserDetailsService(OAuth2ExchangeCodeStore exchangeCodeStore) {
		this.exchangeCodeStore = exchangeCodeStore;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		if (!StringUtils.hasText(username)) {
			throw new UsernameNotFoundException("Username must not be blank");
		}

		return exchangeCodeStore.findUserByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("No registered user found for username: " + username));
	}

	public void register(UserPrinciple userPrinciple) {
		exchangeCodeStore.saveUser(userPrinciple);
	}
}
