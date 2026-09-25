package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.dto.AdminTemplateCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.TemplateLayoutResponseDto;

import java.util.List;
import java.util.UUID;

/**
 * Template administration. A template is only accepted when its layout exists in code and every
 * section it shows is one that layout can draw, so every stored row stays renderable.
 */
public interface AdminTemplateService {

    List<TemplateLayoutResponseDto> listLayouts();

    /** Every template, inactive included, in picker order. */
    List<AdminTemplateResponseDto> list();

    AdminTemplateResponseDto create(UUID actorId, AdminTemplateCreateRequestDto request);

    AdminTemplateResponseDto update(UUID actorId, UUID templateId, AdminTemplateUpdateRequestDto request);

    /**
     * @throws io.github.mainalisandeep.cvgen.common.exception.ConflictException when CVs still use it,
     *                                                                          or it is the default template
     */
    void delete(UUID actorId, UUID templateId);
}
