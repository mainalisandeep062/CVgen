package io.github.mainalisandeep.cvgen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A job to compare a CV against. Nothing here is stored; the analysis is computed and returned.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CvAnalysisRequestDto {

    /** Optional. When given, the headline is checked against it. */
    @Size(max = 200, message = "{validation.analysis.job.title.size}")
    private String jobTitle;

    /** The posting as pasted. The cap keeps a single request from scanning a novel. */
    @NotBlank(message = "{validation.analysis.job.description.required}")
    @Size(max = 20000, message = "{validation.analysis.job.description.size}")
    private String jobDescription;
}
