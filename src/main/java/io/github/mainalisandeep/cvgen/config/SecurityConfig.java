package io.github.mainalisandeep.cvgen.config;

import io.github.mainalisandeep.cvgen.security.JwtAuthFilter;
import io.github.mainalisandeep.cvgen.security.oauth2.CustomOAuth2UserService;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2AuthenticationFailureHandler;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final SecurityProperties securityProperties;
	private final CustomOAuth2UserService customOAuth2UserService;
	private final OAuth2AuthenticationSuccessHandler successHandler;
	private final OAuth2AuthenticationFailureHandler failureHandler;
	private final String logoutUrl;
	private final String unauthorizedMessage;
	private final String forbiddenMessage;
	private final String cookieName;
	private final String bearerPrefix;

	public SecurityConfig(
			SecurityProperties securityProperties,
			CustomOAuth2UserService customOAuth2UserService,
			OAuth2AuthenticationSuccessHandler successHandler,
			OAuth2AuthenticationFailureHandler failureHandler,
			@Value("${app.security.logout-url}") String logoutUrl,
			@Value("${app.security.messages.unauthorized}") String unauthorizedMessage,
			@Value("${app.security.messages.forbidden}") String forbiddenMessage,
			@Value("${app.security.oauth2.access-token-cookie-name}") String cookieName,
			@Value("${app.security.oauth2.bearer-prefix}") String bearerPrefix
	) {
		this.securityProperties = securityProperties;
		this.customOAuth2UserService = customOAuth2UserService;
		this.successHandler = successHandler;
		this.failureHandler = failureHandler;
		this.logoutUrl = logoutUrl;
		this.unauthorizedMessage = unauthorizedMessage;
		this.forbiddenMessage = forbiddenMessage;
		this.cookieName = cookieName;
		this.bearerPrefix = bearerPrefix;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.authorizeHttpRequests(authorize -> {
					authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
					authorize.requestMatchers(securityProperties.getPublicPaths().toArray(String[]::new)).permitAll();
					authorize.anyRequest().authenticated();
				})
				.oauth2Login(oauth2 -> oauth2
						.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
						.successHandler(successHandler)
						.failureHandler(failureHandler)
				)
				.logout(logout -> logout
						.logoutUrl(logoutUrl)
						.deleteCookies(cookieName)
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.logoutSuccessUrl(securityProperties.getOauth2().getSuccessRedirectUri())
				)
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.authenticationEntryPoint((request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value(), unauthorizedMessage))
						.accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpStatus.FORBIDDEN.value(), forbiddenMessage))
				)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable);

		return http.build();
	}

	@Bean
	public JwtAuthFilter jwtAuthFilter(io.github.mainalisandeep.cvgen.security.JwtTokenProvider jwtTokenProvider) {
		return new JwtAuthFilter(jwtTokenProvider, bearerPrefix, cookieName);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(new ArrayList<>(securityProperties.getCors().getAllowedOrigins()));
		configuration.setAllowedMethods(new ArrayList<>(securityProperties.getCors().getAllowedMethods()));
		configuration.setAllowedHeaders(new ArrayList<>(securityProperties.getCors().getAllowedHeaders()));
		configuration.setExposedHeaders(new ArrayList<>(securityProperties.getCors().getExposedHeaders()));
		configuration.setAllowCredentials(securityProperties.getCors().isAllowCredentials());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
