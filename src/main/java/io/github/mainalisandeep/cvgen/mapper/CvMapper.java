package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.dto.CvDetailResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvListResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvSummaryResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.enums.CvTemplate;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Single place where a {@link Cv} entity is translated for the layers above it.
 */
@Component
public class CvMapper {

    /** List row: columns only, never a read into the content document. */
    public CvSummaryResponseDto toSummaryDto(Cv cv) {
        return new CvSummaryResponseDto(
                cv.getId(),
                cv.getTitle(),
                cv.getTemplateKey(),
                cv.getLocale(),
                cv.getStatus(),
                toLocalDateTime(cv.getCreatedAt()),
                toLocalDateTime(cv.getUpdatedAt())
        );
    }

    public CvDetailResponseDto toDetailDto(Cv cv) {
        return new CvDetailResponseDto(
                cv.getId(),
                cv.getTitle(),
                cv.getTemplateKey(),
                cv.getLocale(),
                cv.getStatus(),
                cv.getContent(),
                cv.getVersion(),
                toLocalDateTime(cv.getCreatedAt()),
                toLocalDateTime(cv.getUpdatedAt())
        );
    }

    public CvListResponseDto toListDto(Page<Cv> page) {
        List<CvSummaryResponseDto> items = page.getContent().stream()
                .map(this::toSummaryDto)
                .toList();

        return new CvListResponseDto(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public CvTemplateResponseDto toTemplateDto(CvTemplate template) {
        return new CvTemplateResponseDto(
                template.getKey(),
                template.getDisplayName(),
                template.getDescription(),
                template.getSupportedSections()
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
