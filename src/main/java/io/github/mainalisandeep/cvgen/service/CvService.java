package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.CvCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvListResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvMetaUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvUpdateRequestDto;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Owns every CV a user has, and owns the ownership check with it.
 * <p>
 * Every method takes the caller's id first and loads through it. Controllers pass that id down;
 * they never decide who may see what. A CV belonging to another user is reported as missing,
 * not as forbidden - a 403 confirms the id exists and turns a guess into an enumeration oracle.
 */
public interface CvService {

    /**
     * @throws io.github.mainalisandeep.cvgen.common.exception.ConflictException when the user is
     *                                                                          already at the per-user cap
     */
    CvDetailResponseDto create(UUID userId, CvCreateRequestDto request);

    /** The caller's own CVs, most recently edited first unless the page says otherwise. */
    CvListResponseDto list(UUID userId, Pageable pageable);

    CvDetailResponseDto get(UUID userId, UUID cvId);

    /** Replaces the whole content document. Partial section updates get their own endpoint later. */
    CvDetailResponseDto replaceContent(UUID userId, UUID cvId, CvUpdateRequestDto request);

    /** Updates list metadata only; null fields are left as they are. */
    CvDetailResponseDto updateMeta(UUID userId, UUID cvId, CvMetaUpdateRequestDto request);

    /**
     * Hard delete. There is no restore path and no soft-delete column: a CV the user deleted
     * must actually be gone, and account deletion later has one less thing to cascade around.
     */
    void delete(UUID userId, UUID cvId);
}
