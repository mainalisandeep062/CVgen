package io.github.mainalisandeep.cvgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.service.impl.CvTextParser;
import io.github.mainalisandeep.cvgen.service.impl.KeywordVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The import parser against the kinds of text real CVs extract to. Plain unit test: no Spring,
 * no database - the parser is pure.
 */
class CvTextParserTest {

    private final CvTextParser parser = new CvTextParser(new ObjectMapper(), new KeywordVocabulary());

    private static final String TYPICAL = """
            Aarav Sharma
            Senior Java Developer
            aarav.sharma@example.com | +977 980-1234567 | Kathmandu, Nepal
            linkedin.com/in/aaravsharma | github.com/aarav

            PROFESSIONAL SUMMARY
            Backend engineer with six years building payment systems.
            Comfortable owning services end to end.

            WORK EXPERIENCE
            Senior Backend Engineer
            Fonepay Payment Service
            Jan 2022 – Present
            • Cut settlement time by 40% with Spring Boot services.
            • Led a team of four.
            Software Engineer at Leapfrog Technology
            Mar 2019 - Dec 2021
            • Built REST APIs in Java.

            EDUCATION
            Bachelor of Engineering in Computer
            Pulchowk Campus, Tribhuvan University
            2014 – 2018

            SKILLS
            Languages: Java, Kotlin, SQL
            Frameworks: Spring Boot, React
            Leadership, Communication

            PROJECTS
            Khata Book \u2014 github.com/aarav/khata
            • Offline-first ledger app for small shops.
            Nepali Date Converter
            • Library for BS/AD conversion.

            LANGUAGES
            English (Fluent), Nepali (Native)
            """;

    @Test
    @DisplayName("Header lines become name, headline and contact details")
    void readsHeader() {
        JsonNode basics = parser.parse(TYPICAL).document().get("basics");

        assertThat(basics.get("fullName").asText()).isEqualTo("Aarav Sharma");
        assertThat(basics.get("headline").asText()).isEqualTo("Senior Java Developer");
        assertThat(basics.get("email").asText()).isEqualTo("aarav.sharma@example.com");
        assertThat(basics.get("phone").asText()).isEqualTo("+977 980-1234567");
        assertThat(basics.get("location").asText()).isEqualTo("Kathmandu, Nepal");
        assertThat(basics.get("links").findValuesAsText("label")).containsExactly("LinkedIn", "GitHub");
    }

    @Test
    @DisplayName("Experience entries split at date ranges, with role, company and dates")
    void readsExperience() {
        JsonNode items = section(parser.parse(TYPICAL).document(), CvSectionType.EXPERIENCE).get("items");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("role").asText()).isEqualTo("Senior Backend Engineer");
        assertThat(items.get(0).get("company").asText()).isEqualTo("Fonepay Payment Service");
        assertThat(items.get(0).get("startDate").asText()).isEqualTo("2022-01");
        assertThat(items.get(0).get("current").asBoolean()).isTrue();
        assertThat(items.get(0).get("description").asText())
                .isEqualTo("Cut settlement time by 40% with Spring Boot services.\nLed a team of four.");

        assertThat(items.get(1).get("role").asText()).isEqualTo("Software Engineer");
        assertThat(items.get(1).get("company").asText()).isEqualTo("Leapfrog Technology");
        assertThat(items.get(1).get("startDate").asText()).isEqualTo("2019-03");
        assertThat(items.get(1).get("endDate").asText()).isEqualTo("2021-12");
        assertThat(items.get(1).get("current").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Education finds degree and institution by keyword, years as years")
    void readsEducation() {
        JsonNode item = section(parser.parse(TYPICAL).document(), CvSectionType.EDUCATION).get("items").get(0);

        assertThat(item.get("degree").asText()).isEqualTo("Bachelor of Engineering in Computer");
        assertThat(item.get("institution").asText()).isEqualTo("Pulchowk Campus, Tribhuvan University");
        assertThat(item.get("startDate").asText()).isEqualTo("2014");
        assertThat(item.get("endDate").asText()).isEqualTo("2018");
    }

    @Test
    @DisplayName("Skills drop group labels and separate soft skills from technical ones")
    void readsSkills() {
        JsonNode items = section(parser.parse(TYPICAL).document(), CvSectionType.SKILLS).get("items");

        assertThat(keywords(items.get(0))).containsExactly("Java", "Kotlin", "SQL", "Spring Boot", "React");
        assertThat(items.get(0).get("name").asText()).isEqualTo("Technical");
        assertThat(keywords(items.get(1))).containsExactly("Leadership", "Communication");
        assertThat(items.get(1).get("name").asText()).isEqualTo("Soft");
    }

    @Test
    @DisplayName("Projects split per title line, links pulled out of the title")
    void readsProjects() {
        JsonNode items = section(parser.parse(TYPICAL).document(), CvSectionType.PROJECTS).get("items");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("name").asText()).isEqualTo("Khata Book");
        assertThat(items.get(0).get("url").asText()).isEqualTo("github.com/aarav/khata");
        assertThat(items.get(0).get("description").asText()).isEqualTo("Offline-first ledger app for small shops.");
        assertThat(items.get(1).get("name").asText()).isEqualTo("Nepali Date Converter");
    }

    @Test
    @DisplayName("Summary is rejoined into prose and languages split per entry")
    void readsSummaryAndLanguages() {
        JsonNode document = parser.parse(TYPICAL).document();

        assertThat(section(document, CvSectionType.SUMMARY).get("items").get(0).get("text").asText())
                .isEqualTo("Backend engineer with six years building payment systems. Comfortable owning services end to end.");
        assertThat(section(document, CvSectionType.LANGUAGES).get("items").findValuesAsText("name"))
                .containsExactly("English (Fluent)", "Nepali (Native)");
    }

    @Test
    @DisplayName("Headings are recognised in any case, with or without a colon and inline content")
    void recognisesHeadingVariants() {
        String text = """
                Jane Doe
                Skills: Python, Django
                Employment History:
                Data Engineer, Daraz
                06/2020 to 08/2023
                """;

        CvTextParser.Parsed parsed = parser.parse(text);

        assertThat(parsed.sectionsFound()).contains(CvSectionType.SKILLS, CvSectionType.EXPERIENCE);
        JsonNode job = section(parsed.document(), CvSectionType.EXPERIENCE).get("items").get(0);
        assertThat(job.get("role").asText()).isEqualTo("Data Engineer");
        assertThat(job.get("company").asText()).isEqualTo("Daraz");
        assertThat(job.get("startDate").asText()).isEqualTo("2020-06");
        assertThat(job.get("endDate").asText()).isEqualTo("2023-08");
    }

    @Test
    @DisplayName("Text with no recognisable headings still yields a header and no invented sections")
    void toleratesUnstructuredText() {
        CvTextParser.Parsed parsed = parser.parse("Ram Bahadur\nram@example.com\nI like building things.");

        assertThat(parsed.sectionsFound()).isEmpty();
        assertThat(parsed.document().get("basics").get("fullName").asText()).isEqualTo("Ram Bahadur");
        assertThat(parsed.document().get("sections")).isEmpty();
    }

    private static JsonNode section(JsonNode document, CvSectionType type) {
        for (JsonNode section : document.get("sections")) {
            if (type.name().equals(section.get("type").asText())) {
                return section;
            }
        }
        throw new AssertionError("No " + type + " section in " + document);
    }

    private static List<String> keywords(JsonNode group) {
        List<String> values = new ArrayList<>();
        group.get("keywords").forEach(keyword -> values.add(keyword.asText()));
        return values;
    }
}
