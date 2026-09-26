package io.github.mainalisandeep.cvgen.dto;

import java.util.List;

/**
 * How well one CV covers one job description.
 * <p>
 * Keyword coverage, not a hiring prediction: every number is a count the user can check against
 * the lists beside it.
 *
 * @param coverage      matched / (matched + missing), 0-100; optional terms are not counted against it
 * @param matched       terms from the posting that the CV contains, in posting order
 * @param missing       required terms the CV lacks
 * @param optional      terms the CV lacks that the posting only calls nice-to-have
 * @param categories    the three bars
 * @param warnings      structural checks, passed ones included so the list is never empty
 * @param suggestions   concrete edits for the most important gaps
 * @param requiredYears years of experience the posting asks for, or {@code null}
 * @param cvYears       years covered by the CV's experience dates, overlaps counted once
 */
public record CvAnalysisResponseDto(
        int coverage,
        List<String> matched,
        List<String> missing,
        List<String> optional,
        Categories categories,
        List<Check> warnings,
        List<String> suggestions,
        Counts counts,
        Integer requiredYears,
        double cvYears
) {

    /** Each 0-100. */
    public record Categories(int skills, int keywords, int experience) {
    }

    /**
     * @param code    stable identifier for the client, e.g. {@code MISSING_SUMMARY}
     * @param passed  {@code true} for a check that found nothing wrong
     * @param message already localised
     */
    public record Check(String code, boolean passed, String message) {
    }

    /** @param warnings failed checks only */
    public record Counts(int matched, int missing, int warnings) {
    }
}
