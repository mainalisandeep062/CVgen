package io.github.mainalisandeep.cvgen.enums;

/**
 * The section kinds the renderer knows how to draw.
 * <p>
 * Deliberately <em>not</em> used to deserialise {@code cvs.content}: the document is stored and
 * read as a raw tree, so a section type this server has never heard of survives a round trip
 * instead of being rejected. This enum only seeds the starter document and, later, tells the
 * renderer which template fragment to use. Unknown types are ignored, never dropped.
 */
public enum CvSectionType {
    SUMMARY,
    EXPERIENCE,
    EDUCATION,
    SKILLS,
    PROJECTS,
    CERTIFICATIONS,
    LANGUAGES,
    CUSTOM
}
