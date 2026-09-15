package io.github.mainalisandeep.cvgen.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The template registry: every look a CV can be rendered with.
 * <p>
 * An enum rather than a table on purpose. Templates ship with the code that draws them, so a row
 * nobody can render is a bug this shape makes impossible; a table earns its place only once users
 * author their own.
 * <p>
 * {@link #getKey()} is what {@code cvs.template_key} stores, not {@link #name()}, so renaming a
 * constant never orphans a stored CV.
 */
public enum CvTemplate {

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

    CvTemplate(String key, String displayName, String description, List<CvSectionType> supportedSections) {
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

    /** Section types this template draws. Anything else in the document is kept but not rendered. */
    public List<CvSectionType> getSupportedSections() {
        return supportedSections;
    }

    public static Optional<CvTemplate> fromKey(String key) {
        return Arrays.stream(values())
                .filter(template -> template.key.equals(key))
                .findFirst();
    }
}
