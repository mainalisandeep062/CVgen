package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.enums.CvTemplate;
import io.github.mainalisandeep.cvgen.mapper.CvMapper;
import io.github.mainalisandeep.cvgen.service.CvTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CvTemplateServiceImpl implements CvTemplateService {

    private final CvMapper cvMapper;

    @Override
    public List<CvTemplateResponseDto> list() {
        return Arrays.stream(CvTemplate.values())
                .map(cvMapper::toTemplateDto)
                .toList();
    }

    /**
     * A key the registry does not know would store fine and fail only at render time, far from
     * the request that caused it - so it is refused at the door instead.
     */
    @Override
    public String requireKnown(String key) {
        String candidate = key == null ? null : key.trim();
        return CvTemplate.fromKey(candidate)
                .map(CvTemplate::getKey)
                .orElseThrow(() -> new BadRequestException(ErrorConstantValue.CV_TEMPLATE_UNKNOWN, candidate));
    }
}
