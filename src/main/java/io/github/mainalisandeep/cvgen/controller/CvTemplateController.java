package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.TemplateUnlockResponseDto;
import io.github.mainalisandeep.cvgen.security.util.JwtTokenUtil;
import io.github.mainalisandeep.cvgen.service.CvTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The template picker, and unlocking a premium template with credits. */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class CvTemplateController extends BaseController {

    private final CvTemplateService cvTemplateService;
    private final JwtTokenUtil jwtTokenUtil;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<List<CvTemplateResponseDto>>> listTemplates() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, cvTemplateService.list(jwtTokenUtil.getCurrentUserId()), FieldConstantValue.CV_TEMPLATES);
    }

    /** Spends the template's credit cost once; repeating it charges nothing. */
    @PostMapping("/{templateKey}/unlock")
    public ResponseEntity<GlobalApiResponse<TemplateUnlockResponseDto>> unlockTemplate(@PathVariable String templateKey) {
        return ok(SuccessResponseConstant.TEMPLATE_UNLOCKED,
                cvTemplateService.unlock(jwtTokenUtil.getCurrentUserId(), templateKey));
    }
}
