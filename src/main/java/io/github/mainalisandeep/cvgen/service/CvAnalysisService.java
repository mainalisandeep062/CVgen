package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CvAnalysisRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvAnalysisResponseDto;

import java.util.UUID;

/** Compares one of the caller's CVs with a job description. Computes, never stores. */
public interface CvAnalysisService {

    /**
     * @throws io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException when the CV
     *                                                                                  does not exist or is not the caller's
     */
    CvAnalysisResponseDto analyze(UUID userId, UUID cvId, CvAnalysisRequestDto request);
}
