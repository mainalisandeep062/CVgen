package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.entity.CvTemplate;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.enums.CvTemplateLayout;
import io.github.mainalisandeep.cvgen.records.RenderedCv;
import io.github.mainalisandeep.cvgen.records.RenderedHtml;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.repository.CvTemplateRepository;
import io.github.mainalisandeep.cvgen.service.CvRenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CvRenderServiceImpl implements CvRenderService {

    /** Used when a template row carries no colour, or one that is not a plain hex value. */
    static final String DEFAULT_ACCENT = "#3F33C2";

    private static final Pattern HEX_COLOUR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern NOT_FILENAME_SAFE = Pattern.compile("[^A-Za-z0-9]+");
    private static final String TEMPLATE_DIRECTORY = "cv/";

    private final CvRepository cvRepository;
    private final CvTemplateRepository cvTemplateRepository;
    private final CvDocumentReader cvDocumentReader;
    private final TemplateEngine templateEngine;

    @Override
    @Transactional(readOnly = true)
    public RenderedHtml render(UUID userId, UUID cvId) {
        Cv cv = cvRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CV));

        // A retired template still renders: the CV was written against it, and deactivating a
        // template hides it from the picker, it does not break CVs already using it.
        CvTemplate template = cvTemplateRepository.findByTemplateKey(cv.getTemplateKey()).orElse(null);
        CvTemplateLayout layout = template == null
                ? CvTemplateLayout.CLASSIC
                : CvTemplateLayout.fromKey(template.getLayout()).orElse(CvTemplateLayout.CLASSIC);
        List<CvSectionType> sections = template == null || template.getSupportedSections().isEmpty()
                ? layout.getSupportedSections()
                : template.getSupportedSections();

        RenderedCv rendered = cvDocumentReader.read(cv.getContent(), sections, accent(template));

        Context context = new Context(Locale.forLanguageTag(cv.getLocale()));
        context.setVariable("cv", rendered);
        context.setVariable("accent", rendered.accentColor());
        context.setVariable("locale", cv.getLocale());

        String html = templateEngine.process(TEMPLATE_DIRECTORY + layout.getKey(), context);
        return new RenderedHtml(html, fileName(rendered.fullName(), cv.getTitle()));
    }

    /**
     * The colour lands inside a stylesheet, so only a bare {@code #rrggbb} is let through; the
     * admin form validates it too, this is the backstop.
     */
    private static String accent(CvTemplate template) {
        String colour = template == null ? null : template.getAccentColor();
        return colour != null && HEX_COLOUR.matcher(colour).matches() ? colour : DEFAULT_ACCENT;
    }

    /** {@code Aarav_Sharma_CV.pdf}; accents folded to ASCII, falling back to the CV title, then {@code CV}. */
    static String fileName(String fullName, String title) {
        String base = asciiWords(fullName);
        if (base.isEmpty()) {
            base = asciiWords(title);
        }
        return (base.isEmpty() ? "CV" : base + "_CV") + ".pdf";
    }

    private static String asciiWords(String value) {
        if (value == null) {
            return "";
        }
        String folded = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String words = NOT_FILENAME_SAFE.matcher(folded).replaceAll("_").replaceAll("^_+|_+$", "");
        return words.length() > 60 ? words.substring(0, 60) : words;
    }
}
