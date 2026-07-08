package io.github.mainalisandeep.cvgen.security.oauth2;

import io.github.mainalisandeep.cvgen.security.CustomUserDetailsService;
import io.github.mainalisandeep.cvgen.security.UserPrinciple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
	private final CustomUserDetailsService userDetailsService;
	private final OAuth2UserInfoFactory userInfoFactory;
	private final String defaultRole;

	public CustomOAuth2UserService(
			CustomUserDetailsService userDetailsService,
			OAuth2UserInfoFactory userInfoFactory,
			@Value("${app.security.oauth2.default-role}") String defaultRole
	) {
		this.userDetailsService = userDetailsService;
		this.userInfoFactory = userInfoFactory;
		this.defaultRole = defaultRole;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oauth2User = delegate.loadUser(userRequest);
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		OAuth2UserInfo userInfo = userInfoFactory.getUserInfo(registrationId, oauth2User.getAttributes());

		Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
		authorities.add(new SimpleGrantedAuthority(defaultRole));

		String username = firstNonBlank(userInfo.getEmail(), userInfo.getName(), registrationId + ":" + userInfo.getId());
		UserPrinciple principal = UserPrinciple.oauth2User(
				userInfo.getId(),
				registrationId,
				userInfo.getName(),
				username,
				userInfo.getEmail(),
				userInfo.getImageUrl(),
				oauth2User.getAttributes(),
				authorities
		);

		userDetailsService.register(principal);
		return principal;
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
