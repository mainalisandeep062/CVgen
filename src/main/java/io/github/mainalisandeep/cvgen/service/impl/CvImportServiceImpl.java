package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.dto.CvImportResponseDto;
import io.github.mainalisandeep.cvgen.dto.GithubRepositoryResponseDto;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.service.CvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CvImportServiceImpl implements CvImportService {

    private static final long BYTES_PER_MB = 1024L * 1024;

    private final CvFileTextExtractor textExtractor;
    private final CvTextParser textParser;
    private final CvContentValidator contentValidator;
    private final CvProperties cvProperties;
    private final GithubClient githubClient;

    @Override
    public CvImportResponseDto readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorConstantValue.FILE_EMPTY);
        }
        if (file.getSize() > cvProperties.getImportMaxBytes()) {
            throw new BadRequestException(ErrorConstantValue.CV_IMPORT_TOO_LARGE,
                    Math.max(1, cvProperties.getImportMaxBytes() / BYTES_PER_MB));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException(ErrorConstantValue.FILE_EMPTY);
        }

        String text = textExtractor.extract(bytes);
        if (text.strip().length() < 20) {
            throw new BadRequestException(ErrorConstantValue.CV_IMPORT_NO_TEXT);
        }

        CvTextParser.Parsed parsed = textParser.parse(text);
        // The same gate every saved document passes, so a draft that could not be saved is refused now.
        JsonNode content = contentValidator.validate(parsed.document());

        JsonNode basics = content.path("basics");
        CvImportResponseDto.Detected detected = new CvImportResponseDto.Detected(
                basics.path("fullName").asText(""),
                basics.path("headline").asText(""),
                basics.path("email").asText(""),
                basics.path("phone").asText(""),
                itemCount(content, CvSectionType.EXPERIENCE),
                itemCount(content, CvSectionType.EDUCATION),
                skillCount(content),
                itemCount(content, CvSectionType.PROJECTS)
        );

        List<String> sections = parsed.sectionsFound().stream().map(Enum::name).sorted().toList();
        return new CvImportResponseDto(content, detected, sections);
    }

    @Override
    public List<GithubRepositoryResponseDto> listGithubRepositories(String username) {
        JsonNode repositories = githubClient.ownedRepositories(username == null ? null : username.strip());

        List<GithubRepositoryResponseDto> result = new ArrayList<>();
        if (repositories != null && repositories.isArray()) {
            for (JsonNode repo : repositories) {
                // Forks are someone else's work; archived ones stay, they are still the user's projects.
                if (repo.path("fork").asBoolean(false) || repo.path("private").asBoolean(false)) {
                    continue;
                }
                result.add(new GithubRepositoryResponseDto(
                        repo.path("name").asText(""),
                        textOrNull(repo.get("description")),
                        repo.path("html_url").asText(""),
                        textOrNull(repo.get("homepage")),
                        textOrNull(repo.get("language")),
                        repo.path("stargazers_count").asInt(0),
                        timestamp(repo.path("pushed_at").asText(null))
                ));
            }
        }
        result.sort(Comparator.comparing(GithubRepositoryResponseDto::pushedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private static int itemCount(JsonNode content, CvSectionType type) {
        for (JsonNode section : content.path("sections")) {
            if (type.name().equals(section.path("type").asText())) {
                return section.path("items").size();
            }
        }
        return 0;
    }

    private static int skillCount(JsonNode content) {
        int count = 0;
        for (JsonNode section : content.path("sections")) {
            if (CvSectionType.SKILLS.name().equals(section.path("type").asText())) {
                for (JsonNode group : section.path("items")) {
                    count += group.path("keywords").size();
                }
            }
        }
        return count;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    /** GitHub timestamps are ISO-8601 with an offset; converted like every other timestamp this API returns. */
    private static LocalDateTime timestamp(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(OffsetDateTime.parse(value).toInstant(), ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
