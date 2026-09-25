package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.records.RenderedHtml;

import java.util.UUID;

/**
 * Draws one of the caller's CVs with the layout its template names.
 * <p>
 * The same HTML feeds the PDF, so what the layout shows is exactly what gets exported.
 */
public interface CvRenderService {

    /**
     * @throws io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException when the CV
     *                                                                                  does not exist or is not the caller's
     */
    RenderedHtml render(UUID userId, UUID cvId);
}
