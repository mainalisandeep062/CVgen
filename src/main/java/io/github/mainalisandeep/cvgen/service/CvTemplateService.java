package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;

import java.util.List;

/**
 * Read side of the template catalogue as users see it, and the single check that a template key
 * may be chosen.
 */
public interface CvTemplateService {

    /** Every active template, in picker order. */
    List<CvTemplateResponseDto> list();

    /**
     * @return the stored form of {@code key}
     * @throws io.github.mainalisandeep.cvgen.common.exception.BadRequestException when no active
     *                                                                            template has that key
     */
    String requireKnown(String key);
}
