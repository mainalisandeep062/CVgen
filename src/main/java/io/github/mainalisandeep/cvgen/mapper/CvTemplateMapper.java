package io.github.mainalisandeep.cvgen.mapper;

import io.github.mainalisandeep.cvgen.dto.AdminTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.CvTemplateResponseDto;
import io.github.mainalisandeep.cvgen.dto.TemplateLayoutResponseDto;
import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import io.github.mainalisandeep.cvgen.enums.CvTemplateLayout;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Single place where templates and layouts are translated for the layers above them.
 */
@Component
public class CvTemplateMapper {

    /** Picker entry. Carries nothing an ordinary user should not see, such as usage counts. */
    public CvTemplateResponseDto toPublicDto(CvTemplate template, boolean unlocked) {
        return new CvTemplateResponseDto(
                template.getTemplateKey(),
                template.getName(),
                template.getDescription(),
                List.copyOf(template.getSupportedSections()),
                template.getLayout(),
                template.getAccentColor(),
                template.isPremium(),
                template.getCreditCost(),
                !template.isPremium() || unlocked
        );
    }

    public AdminTemplateResponseDto toAdminDto(CvTemplate template, long cvCount) {
        return new AdminTemplateResponseDto(
                template.getId(),
                template.getTemplateKey(),
                template.getName(),
                template.getDescription(),
                template.getLayout(),
                template.getAccentColor(),
                List.copyOf(template.getSupportedSections()),
                template.isPremium(),
                template.getCreditCost(),
                template.isActive(),
                template.getSortOrder(),
                cvCount,
                toLocalDateTime(template.getCreatedAt()),
                toLocalDateTime(template.getUpdatedAt())
        );
    }

    public TemplateLayoutResponseDto toLayoutDto(CvTemplateLayout layout) {
        return new TemplateLayoutResponseDto(layout.getKey(), layout.getDisplayName(), layout.getSupportedSections());
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
