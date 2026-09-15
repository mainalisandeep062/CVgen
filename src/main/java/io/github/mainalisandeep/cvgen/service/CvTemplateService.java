package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;

import java.util.List;

/**
 * Read side of the template registry, and the single check that a template key is real.
 */
public interface CvTemplateService {

    /** Every template a CV can be switched to, in picker order. */
    List<CvTemplateResponseDto> list();

    /**
     * @return the stored form of {@code key}
     * @throws io.github.mainalisandeep.cvgen.common.exception.BadRequestException when no template
     *                                                                            has that key
     */
    String requireKnown(String key);
}
