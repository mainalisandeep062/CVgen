package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.TemplateUnlockResponseDto;

import java.util.List;
import java.util.UUID;

/**
 * Read side of the template catalogue as users see it, the checks that a template key may be
 * chosen, and unlocking premium templates with credits.
 */
public interface CvTemplateService {

    /** Every active template, in picker order, with whether the caller may use it. */
    List<CvTemplateResponseDto> list(UUID userId);

    /**
     * @return the stored form of {@code key}
     * @throws io.github.mainalisandeep.cvgen.common.exception.BadRequestException when no active
     *                                                                            template has that key
     */
    String requireKnown(String key);

    /**
     * {@link #requireKnown} plus: a premium template must have been unlocked by {@code userId}.
     *
     * @throws io.github.mainalisandeep.cvgen.common.exception.ForbiddenException when it is premium and locked
     */
    String requireUsable(UUID userId, String key);

    /**
     * Pays for a premium template with credits, once. Unlocking one already unlocked, or a free one,
     * charges nothing and returns it as it is.
     *
     * @throws io.github.mainalisandeep.cvgen.common.exception.BadRequestException when the balance is too low
     */
    TemplateUnlockResponseDto unlock(UUID userId, String key);
}
