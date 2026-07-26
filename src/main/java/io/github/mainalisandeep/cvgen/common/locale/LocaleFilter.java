package io.github.mainalisandeep.cvgen.common.locale;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the request locale (Accept-Language) to {@link LocaleThreadStorage} and to
 * Spring's {@link LocaleContextHolder}, then clears both once the request completes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocaleFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            LocaleThreadStorage.setLocale(request.getLocale());
            LocaleContextHolder.setLocale(request.getLocale());
            filterChain.doFilter(request, response);
        } finally {
            LocaleThreadStorage.clear();
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
