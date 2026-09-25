package io.github.mainalisandeep.cvgen.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The layouts a CV can be drawn with: code-level renderers, one constant per renderer.
 * <p>
 * Templates used to be this enum. They are now rows in {@code cv_templates}, managed by admins, and
 * each row is a branded variant of exactly one layout - its name, accent colour, pricing and which of
 * the layout's sections it shows. The split keeps the original guarantee: a template nobody can render
 * is still impossible, because a row is refused unless its layout exists here and its sections are a
 * subset of {@link #getSupportedSections()}. Adding a layout remains a code change; adding a template
 * does not.
 * <p>
 * {@link #getKey()} is what {@code cv_templates.layout} stores, not {@link #name()}, so renaming a
 * constant never orphans a stored template.
 */
public enum CvTemplateLayout {

    CLASSIC(
            "classic",
            "Classic",
            "Single column with clear section rules. Reads cleanly through any applicant tracking system.",
            List.of(
                    CvSectionType.SUMMARY,
                    CvSectionType.EXPERIENCE,
                    CvSectionType.EDUCATION,
                    CvSectionType.SKILLS,
                    CvSectionType.PROJECTS,
                    CvSectionType.LANGUAGES
            )
    );

    private final String key;
    private final String displayName;
    private final String description;
    private final List<CvSectionType> supportedSections;

    CvTemplateLayout(String key, String displayName, String description, List<CvSectionType> supportedSections) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.supportedSections = supportedSections;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** Section types this layout can draw. Anything else in the document is kept but not rendered. */
    public List<CvSectionType> getSupportedSections() {
        return supportedSections;
    }

    public static Optional<CvTemplateLayout> fromKey(String key) {
        return Arrays.stream(values())
                .filter(layout -> layout.key.equals(key))
                .findFirst();
    }
}
