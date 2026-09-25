package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import io.github.mainalisandeep.cvgen.mapper.CvTemplateMapper;
import io.github.mainalisandeep.cvgen.repository.CvTemplateRepository;
import io.github.mainalisandeep.cvgen.service.CvTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CvTemplateServiceImpl implements CvTemplateService {

    private final CvTemplateRepository cvTemplateRepository;
    private final CvTemplateMapper cvTemplateMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CvTemplateResponseDto> list() {
        return cvTemplateRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(cvTemplateMapper::toPublicDto)
                .toList();
    }

    /**
     * A key the catalogue does not know would store fine and fail only at render time, far from
     * the request that caused it - so it is refused at the door instead. An inactive template is
     * refused the same way: it is hidden from the picker, so choosing it is not a thing a client can do.
     */
    @Override
    @Transactional(readOnly = true)
    public String requireKnown(String key) {
        String candidate = key == null ? null : key.trim();
        return cvTemplateRepository.findByTemplateKeyAndActiveTrue(candidate)
                .map(CvTemplate::getTemplateKey)
                .orElseThrow(() -> new BadRequestException(ErrorConstantValue.CV_TEMPLATE_UNKNOWN, candidate));
    }
}
