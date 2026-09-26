package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.records.BinaryContent;

import java.util.UUID;

/** Turns one of the caller's CVs into a downloadable file. */
public interface CvExportService {

    /**
     * Renders synchronously within the configured budget.
     *
     * @throws io.github.mainalisandeep.cvgen.common.exception.ServiceUnavailableException when rendering
     *                                                                                    runs past the budget
     */
    BinaryContent exportPdf(UUID userId, UUID cvId);
}
