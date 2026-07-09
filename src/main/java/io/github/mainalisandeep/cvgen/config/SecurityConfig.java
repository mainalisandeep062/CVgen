package io.github.mainalisandeep.cvgen.config;

import io.github.mainalisandeep.cvgen.config.constants.MatchersConfig;
import io.github.mainalisandeep.cvgen.security.JwtAuthFilter;
import io.github.mainalisandeep.cvgen.security.oauth2.CustomOAuth2UserService;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2AuthenticationFailureHandler;
import io.github.mainalisandeep.cvgen.security.oauth2.OAuth2AuthenticationSuccessHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    public SecurityConfig(
            SecurityProperties securityProperties,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler successHandler,
            OAuth2AuthenticationFailureHandler failureHandler
    ) {
        this.securityProperties = securityProperties;
        this.customOAuth2UserService = customOAuth2UserService;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    authorize.requestMatchers(MatchersConfig.PUBLIC_MATCHERS.toArray(String[]::new)).permitAll();
                    authorize.requestMatchers(MatchersConfig.SWAGGER_MATCHERS.toArray(String[]::new)).permitAll();
                    authorize.requestMatchers("/api/auth/**").permitAll();
                    authorize.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessUrl(securityProperties.getOauth2().getSuccessRedirectUri())
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden"))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(io.github.mainalisandeep.cvgen.security.JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthFilter(jwtTokenProvider);
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
