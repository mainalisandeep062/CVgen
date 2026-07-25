package io.github.mainalisandeep.cvgen.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rejects unauthenticated requests with the same {@link GlobalApiResponse} envelope
 * the controllers use, instead of the servlet container error page.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final CustomMessageSource customMessageSource;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), GlobalApiResponse.builder()
                .status(false)
                .message(customMessageSource.get(ErrorConstantValue.UNAUTHORIZED))
                .build());
    }
}
