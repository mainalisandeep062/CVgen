package io.github.mainalisandeep.cvgen.dto;

import java.time.LocalDateTime;

/**
 * A public repository offered for import as a CV project.
 *
 * @param url      the repository's page on github.com
 * @param homepage the project's own site when the owner set one, otherwise {@code null}
 * @param pushedAt last push, for ordering and for the user to recognise the repo
 */
public record GithubRepositoryResponseDto(
        String name,
        String description,
        String url,
        String homepage,
        String language,
        int stars,
        LocalDateTime pushedAt
) {
}
