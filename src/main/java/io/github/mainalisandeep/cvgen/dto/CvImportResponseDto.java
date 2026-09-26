package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * An uploaded CV read into a draft. Nothing is saved: the client shows {@link #detected()} for
 * review and creates the CV with {@code POST /api/cvs} and {@link #content()} once the user agrees.
 *
 * @param content  v1 content document in the editor's shape
 * @param detected what was found, for the review panel
 * @param sections section types a heading was recognised for, e.g. {@code ["EXPERIENCE","SKILLS"]}
 */
public record CvImportResponseDto(
        JsonNode content,
        Detected detected,
        List<String> sections
) {

    public record Detected(
            String fullName,
            String headline,
            String email,
            String phone,
            int experience,
            int education,
            int skills,
            int projects
    ) {
    }
}
