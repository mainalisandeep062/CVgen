package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ConflictException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.ActivityMessageConstant;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.TemplateLayoutResponseDto;
import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import io.github.mainalisandeep.cvgen.enums.AdminAction;
import io.github.mainalisandeep.cvgen.enums.AdminTargetType;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.enums.CvTemplateLayout;
import io.github.mainalisandeep.cvgen.mapper.CvTemplateMapper;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.CvTemplateRepository;
import io.github.mainalisandeep.cvgen.service.AdminAuditLogService;
import io.github.mainalisandeep.cvgen.service.AdminTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTemplateServiceImpl implements AdminTemplateService {

    private final CvTemplateRepository cvTemplateRepository;
    private final CvRepository cvRepository;
    private final CvTemplateMapper cvTemplateMapper;
    private final CvProperties cvProperties;
    private final AdminAuditLogService adminAuditLogService;
    private final ObjectMapper objectMapper;

    @Override
    public List<TemplateLayoutResponseDto> listLayouts() {
        return Arrays.stream(CvTemplateLayout.values())
                .map(cvTemplateMapper::toLayoutDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTemplateResponseDto> list() {
        Map<String, Long> cvCounts = cvRepository.countByTemplateKeys().stream()
                .collect(Collectors.toMap(CvRepository.TemplateCvCount::getTemplateKey, CvRepository.TemplateCvCount::getCount));
        return cvTemplateRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(template -> cvTemplateMapper.toAdminDto(template, cvCounts.getOrDefault(template.getTemplateKey(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public AdminTemplateResponseDto create(UUID actorId, AdminTemplateCreateRequestDto request) {
        if (cvTemplateRepository.existsByTemplateKey(request.getKey())) {
            throw new ConflictException(ErrorConstantValue.TEMPLATE_KEY_EXISTS);
        }

        CvTemplate template = CvTemplate.builder().templateKey(request.getKey()).build();
        applyEditableFields(template, request);
        cvTemplateRepository.save(template);

        adminAuditLogService.record(actorId, AdminAction.TEMPLATE_CREATED, AdminTargetType.TEMPLATE, template.getId(),
                auditDetails(template), ActivityMessageConstant.AUDIT_TEMPLATE_CREATED, template.getTemplateKey());
        return cvTemplateMapper.toAdminDto(template, 0L);
    }

    @Override
    @Transactional
    public AdminTemplateResponseDto update(UUID actorId, UUID templateId, AdminTemplateUpdateRequestDto request) {
        CvTemplate template = findTemplate(templateId);

        // Hiding the default would break every CV created without an explicit template key.
        if (isDefault(template) && Boolean.FALSE.equals(request.getActive())) {
            throw new ConflictException(ErrorConstantValue.TEMPLATE_DEFAULT);
        }

        applyEditableFields(template, request);

        adminAuditLogService.record(actorId, AdminAction.TEMPLATE_UPDATED, AdminTargetType.TEMPLATE, template.getId(),
                auditDetails(template), ActivityMessageConstant.AUDIT_TEMPLATE_UPDATED, template.getTemplateKey());
        return cvTemplateMapper.toAdminDto(template, cvRepository.countByTemplateKey(template.getTemplateKey()));
    }

    @Override
    @Transactional
    public void delete(UUID actorId, UUID templateId) {
        CvTemplate template = findTemplate(templateId);

        if (isDefault(template)) {
            throw new ConflictException(ErrorConstantValue.TEMPLATE_DEFAULT);
        }
        // fk_cvs_template_key would refuse this too, but as a 500. Deactivating is the way to retire a
        // template that is still in use.
        long cvCount = cvRepository.countByTemplateKey(template.getTemplateKey());
        if (cvCount > 0) {
            throw new ConflictException(ErrorConstantValue.TEMPLATE_IN_USE, cvCount);
        }

        adminAuditLogService.record(actorId, AdminAction.TEMPLATE_DELETED, AdminTargetType.TEMPLATE, template.getId(),
                auditDetails(template), ActivityMessageConstant.AUDIT_TEMPLATE_DELETED, template.getTemplateKey());
        cvTemplateRepository.delete(template);
    }

    /**
     * Create and update share every rule, including the defaults for omitted fields: an update is a full
     * replacement, not a merge.
     */
    private void applyEditableFields(CvTemplate template, AdminTemplateUpdateRequestDto request) {
        CvTemplateLayout layout = CvTemplateLayout.fromKey(request.getLayout().trim())
                .orElseThrow(() -> new BadRequestException(ErrorConstantValue.TEMPLATE_LAYOUT_UNKNOWN, request.getLayout().trim()));
        boolean premium = Boolean.TRUE.equals(request.getPremium());

        template.setName(request.getName().trim());
        template.setDescription(blankToNull(request.getDescription()));
        template.setLayout(layout.getKey());
        template.setAccentColor(blankToNull(request.getAccentColor()));
        template.setSupportedSections(supportedSections(layout, request.getSupportedSections()));
        template.setPremium(premium);
        template.setCreditCost(premium && request.getCreditCost() != null ? request.getCreditCost() : 0);
        template.setActive(request.getActive() == null || request.getActive());
        template.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    /** Omitted means everything the layout draws; anything the layout cannot draw is refused, not dropped. */
    private List<CvSectionType> supportedSections(CvTemplateLayout layout, List<CvSectionType> requested) {
        if (requested == null) {
            return new ArrayList<>(layout.getSupportedSections());
        }
        for (CvSectionType section : requested) {
            if (section == null || !layout.getSupportedSections().contains(section)) {
                throw new BadRequestException(ErrorConstantValue.TEMPLATE_SECTION_UNSUPPORTED, section);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(requested));
    }

    private JsonNode auditDetails(CvTemplate template) {
        return objectMapper.createObjectNode()
                .put("key", template.getTemplateKey())
                .put("name", template.getName())
                .put("layout", template.getLayout())
                .put("active", template.isActive())
                .put("premium", template.isPremium())
                .put("creditCost", template.getCreditCost());
    }

    private boolean isDefault(CvTemplate template) {
        return cvProperties.getDefaultTemplateKey().equals(template.getTemplateKey());
    }

    private CvTemplate findTemplate(UUID templateId) {
        return cvTemplateRepository.findById(templateId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CV_TEMPLATE));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
