package io.github.mainalisandeep.cvgen.security.util;

import io.github.mainalisandeep.cvgen.common.exception.UnauthorizedException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.security.IdentifiedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reads the caller identity out of the security context.
 */
@Component
public class JwtTokenUtil {

    /**
     * Id of the authenticated user.
     *
     * @throws UnauthorizedException when the request carries no usable principal
     */
    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof IdentifiedPrincipal principal
                && principal.getId() != null) {
            return UUID.fromString(principal.getId());
        }

        throw new UnauthorizedException(ErrorConstantValue.UNAUTHORIZED);
    }
}
