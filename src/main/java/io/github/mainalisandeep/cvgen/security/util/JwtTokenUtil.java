package io.github.mainalisandeep.cvgen.security.util;

import io.github.mainalisandeep.cvgen.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtTokenUtil {

    public UUID getUserIdFromToken() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal userPrincipal) {
            return UUID.fromString(userPrincipal.getId());
        }

        return null;
    }
}