package io.github.mainalisandeep.cvgen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final String bearerPrefix;
	private final String accessTokenCookieName;

	public JwtAuthFilter(
			JwtTokenProvider jwtTokenProvider,
			String bearerPrefix,
			String accessTokenCookieName
	) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.bearerPrefix = bearerPrefix;
		this.accessTokenCookieName = accessTokenCookieName;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String token = resolveToken(request);
		if (token != null && jwtTokenProvider.validateToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
			SecurityContextHolder.getContext().setAuthentication(jwtTokenProvider.getAuthentication(token));
		}

		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorizationHeader != null && authorizationHeader.startsWith(bearerPrefix)) {
			return authorizationHeader.substring(bearerPrefix.length()).trim();
		}

		return Optional.ofNullable(request.getCookies())
				.stream()
				.flatMap(Arrays::stream)
				.filter(cookie -> cookie != null && accessTokenCookieName.equals(cookie.getName()))
				.map(Cookie::getValue)
				.filter(value -> value != null && !value.isBlank())
				.findFirst()
				.orElse(null);
	}
}
