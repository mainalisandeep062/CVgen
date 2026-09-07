package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.CvCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvListResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvMetaUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvUpdateRequestDto;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.CvService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * CRUD over the caller's own CVs.
 * <p>
 * Every method passes {@link JwtTokenUtil#getCurrentUserId()} down and makes no authorization
 * decision of its own; {@code CvService} loads through the owner id, so a CV belonging to
 * someone else is reported as missing. Nothing here is public - {@code /api/cvs/**} must stay
 * out of {@code MatchersConfig.PUBLIC_MATCHERS}.
 */
@RestController
@RequestMapping("/api/cvs")
@RequiredArgsConstructor
public class CvController extends BaseController {

    private final CvService cvService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping
    public ResponseEntity<GlobalApiResponse<CvDetailResponseDto>> createCv(
            @Valid @RequestBody CvCreateRequestDto request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return respond(HttpStatus.CREATED, SuccessResponseConstant.CV_CREATED, cvService.create(userId, request));
    }

    /** Own CVs only, most recently edited first. */
    @GetMapping
    public ResponseEntity<GlobalApiResponse<CvListResponseDto>> listCvs(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.FETCH_SUCCESS, cvService.list(userId, pageable), FieldConstantValue.CV_LIST);
    }

    @GetMapping("/{cvId}")
    public ResponseEntity<GlobalApiResponse<CvDetailResponseDto>> getCv(@PathVariable UUID cvId) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.FETCH_SUCCESS, cvService.get(userId, cvId), FieldConstantValue.CV);
    }

    /** Full replacement of the content document. */
    @PutMapping("/{cvId}")
    public ResponseEntity<GlobalApiResponse<CvDetailResponseDto>> replaceCvContent(
            @PathVariable UUID cvId,
            @Valid @RequestBody CvUpdateRequestDto request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.CV_UPDATED, cvService.replaceContent(userId, cvId, request));
    }

    /** Title, template, locale and status - never the document. */
    @PatchMapping("/{cvId}/meta")
    public ResponseEntity<GlobalApiResponse<CvDetailResponseDto>> updateCvMeta(
            @PathVariable UUID cvId,
            @Valid @RequestBody CvMetaUpdateRequestDto request
    ) {
        UUID userId = jwtTokenUtil.getCurrentUserId();
        return ok(SuccessResponseConstant.CV_UPDATED, cvService.updateMeta(userId, cvId, request));
    }

    @DeleteMapping("/{cvId}")
    public ResponseEntity<GlobalApiResponse<Void>> deleteCv(@PathVariable UUID cvId) {
        cvService.delete(jwtTokenUtil.getCurrentUserId(), cvId);
        return ok(SuccessResponseConstant.CV_DELETED, null);
    }
}
