package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateCreateRequestDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.AdminTemplateUpdateRequestDto;
import io.github.mainalisandeep.cvgen.dto.TemplateLayoutResponseDto;
import io.github.mainalisandeep.cvgen.security.AdminAccessGuard;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.AdminTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Template administration; the picker users see is {@link CvTemplateController}. */
@RestController
@RequestMapping("/api/admin/templates")
@PreAuthorize(AdminAccessGuard.IS_ACTIVE_ADMIN)
@RequiredArgsConstructor
public class AdminTemplateController extends BaseController {

    private final AdminTemplateService adminTemplateService;
    private final JwtTokenUtil jwtTokenUtil;

    /** The renderers a template can be based on. Fixed in code, so there is no write side. */
    @GetMapping("/layouts")
    public ResponseEntity<GlobalApiResponse<List<TemplateLayoutResponseDto>>> listLayouts() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminTemplateService.listLayouts(),
                FieldConstantValue.TEMPLATE_LAYOUTS);
    }

    @GetMapping
    public ResponseEntity<GlobalApiResponse<List<AdminTemplateResponseDto>>> listTemplates() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, adminTemplateService.list(), FieldConstantValue.CV_TEMPLATES);
    }

    @PostMapping
    public ResponseEntity<GlobalApiResponse<AdminTemplateResponseDto>> createTemplate(
            @Valid @RequestBody AdminTemplateCreateRequestDto request
    ) {
        return respond(HttpStatus.CREATED, SuccessResponseConstant.TEMPLATE_CREATED,
                adminTemplateService.create(jwtTokenUtil.getCurrentUserId(), request));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<GlobalApiResponse<AdminTemplateResponseDto>> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody AdminTemplateUpdateRequestDto request
    ) {
        return ok(SuccessResponseConstant.TEMPLATE_UPDATED,
                adminTemplateService.update(jwtTokenUtil.getCurrentUserId(), templateId, request));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<GlobalApiResponse<Void>> deleteTemplate(@PathVariable UUID templateId) {
        adminTemplateService.delete(jwtTokenUtil.getCurrentUserId(), templateId);
        return ok(SuccessResponseConstant.TEMPLATE_DELETED, null);
    }
}
