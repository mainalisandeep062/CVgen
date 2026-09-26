package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.records.RenderedCv;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Reads a stored content document into the flat shape layouts draw and analysis reads.
 * <p>
 * Tolerant by design, like the document itself: a missing key is blank, an item that is not an
 * object is skipped, and a section type the caller did not ask for is left out rather than
 * rejected. The document stays the source of truth; this is a read-only view of it.
 * <p>
 * The field names mirror the editor's writer ({@code src/cv/content.js} in the frontend):
 * {@code basics.headline}, experience {@code role/company/startDate/endDate/current},
 * education {@code degree/institution}, skill groups {@code name/keywords}, projects
 * {@code name/url}, languages {@code name}, summary {@code text}.
 */
@Component
public class CvDocumentReader {

    private static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    /**
     * @param supported section types to keep; everything else in the document is skipped
     * @param accent    the template's accent colour, passed through untouched
     */
    public RenderedCv read(JsonNode document, Collection<CvSectionType> supported, String accent) {
        JsonNode basics = document == null ? null : document.get("basics");

        List<String> contact = new ArrayList<>();
        Stream.of("email", "phone", "location").map(key -> text(basics, key)).forEach(contact::add);
        elements(basics == null ? null : basics.get("links"))
                .map(link -> text(link, "url"))
                .forEach(contact::add);
        contact.removeIf(String::isBlank);

        List<RenderedCv.Section> sections = elements(document == null ? null : document.get("sections"))
                .filter(section -> !section.path("visible").isBoolean() || section.path("visible").asBoolean())
                .map(section -> toSection(section, supported))
                .flatMap(Optional::stream)
                .toList();

        return new RenderedCv(text(basics, "fullName"), text(basics, "headline"), contact, sections, accent);
    }

    /** What a parser would pull out of the rendered page, in reading order. */
    public String plainText(RenderedCv cv) {
        StringBuilder out = new StringBuilder();
        line(out, cv.fullName());
        line(out, cv.headline());
        line(out, String.join(" | ", cv.contact()));
        for (RenderedCv.Section section : cv.sections()) {
            out.append('\n');
            line(out, section.title().toUpperCase(Locale.ROOT));
            for (RenderedCv.Item item : section.items()) {
                line(out, item.heading());
                line(out, item.subheading());
                line(out, item.dates());
                line(out, item.url());
                line(out, item.body());
                line(out, String.join(", ", item.keywords()));
            }
        }
        return out.toString().trim();
    }

    private Optional<RenderedCv.Section> toSection(JsonNode section, Collection<CvSectionType> supported) {
        Optional<CvSectionType> type = sectionType(text(section, "type"));
        if (type.isEmpty() || !supported.contains(type.get())) {
            return Optional.empty();
        }

        List<RenderedCv.Item> items = elements(section.get("items"))
                .map(item -> toItem(type.get(), item))
                .filter(CvDocumentReader::hasContent)
                .toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }

        String title = text(section, "title");
        return Optional.of(new RenderedCv.Section(type.get().name(), title.isBlank() ? defaultTitle(type.get()) : title, items));
    }

    private RenderedCv.Item toItem(CvSectionType type, JsonNode item) {
        return switch (type) {
            case SUMMARY -> item(text(item, "text"));
            case EXPERIENCE -> new RenderedCv.Item(text(item, "role"), text(item, "company"),
                    dateRange(text(item, "startDate"), text(item, "endDate"), item.path("current").asBoolean(false)),
                    "", text(item, "description"), List.of());
            case EDUCATION -> new RenderedCv.Item(text(item, "degree"), text(item, "institution"),
                    dateRange(text(item, "startDate"), text(item, "endDate"), false),
                    "", text(item, "description"), List.of());
            case SKILLS -> new RenderedCv.Item(text(item, "name"), "", "", "", "", strings(item.get("keywords")));
            case PROJECTS -> new RenderedCv.Item(text(item, "name"), "", "", safeUrl(text(item, "url")),
                    text(item, "description"), List.of());
            case LANGUAGES -> new RenderedCv.Item(text(item, "name"), "", "", "", "", List.of());
            case CERTIFICATIONS, CUSTOM -> new RenderedCv.Item(firstNonBlank(text(item, "name"), text(item, "title")),
                    text(item, "issuer"), "", safeUrl(text(item, "url")), text(item, "description"), List.of());
        };
    }

    private static RenderedCv.Item item(String body) {
        return new RenderedCv.Item("", "", "", "", body, List.of());
    }

    private static boolean hasContent(RenderedCv.Item item) {
        return !(item.heading().isBlank() && item.subheading().isBlank() && item.body().isBlank()
                && item.url().isBlank() && item.keywords().isEmpty());
    }

    /** {@code 2024-01} to {@code Jan 2024}; a bare year or anything else is shown as typed. */
    static String formatMonth(String value) {
        if (value.matches("\\d{4}-\\d{2}")) {
            int month = Integer.parseInt(value.substring(5));
            if (month >= 1 && month <= 12) {
                return MONTHS[month - 1] + " " + value.substring(0, 4);
            }
        }
        return value;
    }

    private static String dateRange(String start, String end, boolean current) {
        String from = formatMonth(start);
        String to = current ? "Present" : formatMonth(end);
        if (from.isBlank()) {
            return to;
        }
        return to.isBlank() ? from : from + " – " + to;
    }

    /**
     * Only web links survive. The PDF turns this into a clickable annotation, and a
     * {@code javascript:} or {@code file:} target has no business in a CV.
     */
    static String safeUrl(String value) {
        if (value.isBlank()) {
            return "";
        }
        String candidate = value.contains("://") ? value : "https://" + value;
        try {
            String scheme = URI.create(candidate).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) ? candidate : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static Optional<CvSectionType> sectionType(String name) {
        try {
            return Optional.of(CvSectionType.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String defaultTitle(CvSectionType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Stream<JsonNode> elements(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Stream.empty();
        }
        return StreamSupport.stream(node.spliterator(), false).filter(JsonNode::isObject);
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText().trim() : "";
    }

    private static String firstNonBlank(String first, String second) {
        return first.isBlank() ? second : first;
    }

    private static void line(StringBuilder out, String value) {
        if (value != null && !value.isBlank()) {
            out.append(value).append('\n');
        }
    }
}
