package io.github.mainalisandeep.cvgen.controller;

import io.github.mainalisandeep.cvgen.common.controller.BaseController;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.common.message.SuccessResponseConstant;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.service.CvTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The template picker. Same for every user, but only signed-in users build CVs. */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class CvTemplateController extends BaseController {

    private final CvTemplateService cvTemplateService;

    @GetMapping
    public ResponseEntity<GlobalApiResponse<List<CvTemplateResponseDto>>> listTemplates() {
        return ok(SuccessResponseConstant.FETCH_SUCCESS, cvTemplateService.list(), FieldConstantValue.CV_TEMPLATES);
    }
}
