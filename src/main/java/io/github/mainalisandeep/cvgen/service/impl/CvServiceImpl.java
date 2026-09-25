package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.exception.ConflictException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.dto.CvCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvListResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvMetaUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvUpdateRequestDto;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.User;
import io.github.mainalisandeep.cvgen.mapper.CvMapper;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.UserRepository;
import io.github.mainalisandeep.cvgen.service.CvService;
import io.github.mainalisandeep.cvgen.service.CvTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvServiceImpl implements CvService {

    private final CvRepository cvRepository;
    private final UserRepository userRepository;
    private final CvMapper cvMapper;
    private final CvContentValidator cvContentValidator;
    private final CvProperties cvProperties;
    private final CvTemplateService cvTemplateService;

    @Override
    @Transactional
    public CvDetailResponseDto create(UUID userId, CvCreateRequestDto request) {
        User user = findUser(userId);

        if (cvRepository.countByUserId(userId) >= cvProperties.getMaxPerUser()) {
            throw new ConflictException(ErrorConstantValue.CV_LIMIT_REACHED, cvProperties.getMaxPerUser());
        }

        // An omitted content field arrives as Java null, an explicit "content": null as NullNode.
        // Both mean "start me an empty CV" rather than "store nothing".
        JsonNode requested = request.getContent();
        JsonNode content = requested == null || requested.isNull()
                ? cvContentValidator.starterDocument()
                : cvContentValidator.validate(requested);

        Cv cv = Cv.builder()
                .user(user)
                .title(request.getTitle().trim())
                .templateKey(cvTemplateService.requireKnown(
                        orDefault(request.getTemplateKey(), cvProperties.getDefaultTemplateKey())))
                .locale(orDefault(request.getLocale(), cvProperties.getDefaultLocale()))
                .content(content)
                .build();

        return cvMapper.toDetailDto(cvRepository.save(cv));
    }

    @Override
    @Transactional(readOnly = true)
    public CvListResponseDto list(UUID userId, Pageable pageable) {
        Page<Cv> page = cvRepository.findAllByUserId(userId, pageable);
        return cvMapper.toListDto(page);
    }

    @Override
    @Transactional(readOnly = true)
    public CvDetailResponseDto get(UUID userId, UUID cvId) {
        return cvMapper.toDetailDto(findOwnedCv(userId, cvId));
    }

    @Override
    @Transactional
    public CvDetailResponseDto replaceContent(UUID userId, UUID cvId, CvUpdateRequestDto request) {
        Cv cv = findOwnedCv(userId, cvId);
        cv.setContent(cvContentValidator.validate(request.getContent()));
        return cvMapper.toDetailDto(cv);
    }

    @Override
    @Transactional
    public CvDetailResponseDto updateMeta(UUID userId, UUID cvId, CvMetaUpdateRequestDto request) {
        Cv cv = findOwnedCv(userId, cvId);

        // Null means "leave as is", so renaming a CV does not require resending everything else.
        if (request.getTitle() != null) {
            cv.setTitle(request.getTitle().trim());
        }
        // Re-sending the key the CV already has is not a switch, so it is accepted even when an admin has
        // since deactivated that template: the editor round-trips every field, and renaming a CV must not
        // fail because its template was retired. Moving to a different key needs an active template.
        if (request.getTemplateKey() != null && !request.getTemplateKey().trim().equals(cv.getTemplateKey())) {
            cv.setTemplateKey(cvTemplateService.requireKnown(request.getTemplateKey()));
        }
        if (request.getLocale() != null) {
            cv.setLocale(request.getLocale());
        }
        if (request.getStatus() != null) {
            cv.setStatus(request.getStatus());
        }

        return cvMapper.toDetailDto(cv);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID cvId) {
        cvRepository.delete(findOwnedCv(userId, cvId));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.USER));
    }

    /**
     * The single door onto a CV. Loading by id and comparing the owner afterwards is the
     * pattern that eventually gets refactored into a leak, so the owner is part of the query
     * and a foreign CV is reported as missing rather than forbidden.
     */
    private Cv findOwnedCv(UUID userId, UUID cvId) {
        return cvRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CV));
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
