package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the plain text of an existing CV into a v1 content document the editor can open.
 * <p>
 * A heuristic, and honest about it: the result is a pre-filled draft the user reviews before
 * anything is saved, never a record of truth. It works in three passes:
 * <ol>
 *   <li>Split the text at lines that look like section headings ("Work Experience", "SKILLS:").</li>
 *   <li>Read the lines above the first heading as the header: name, headline, contact details.</li>
 *   <li>Parse each section with a reader for its type - date ranges start experience and education
 *   entries, commas and bullets separate skills.</li>
 * </ol>
 * The output uses exactly the field names the editor writes ({@code src/cv/content.js} in the
 * frontend) so an imported CV and a typed one are indistinguishable.
 */
@Component
@RequiredArgsConstructor
public class CvTextParser {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])(\\+?\\d[\\d ()-]{7,}\\d)(?![\\w])");
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:www\\.)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/[^\\s|,•]*)?)");
    private static final Pattern BULLET = Pattern.compile("^[\\s]*[•●▪◦‣∙·*\u2013\u2014-]\\s*");

    private static final String MONTH =
            "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?"
                    + "|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";
    private static final String POINT = "(?:" + MONTH + "\\.?\\s+\\d{4}|\\d{1,2}/\\d{4}|\\d{4}-\\d{2}|\\d{4})";
    private static final String NOW = "(?:present|current|now|ongoing|till date|to date)";
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)(" + POINT + ")\\s*(?:-|\u2013|\u2014|to|until)\\s*(" + POINT + "|" + NOW + ")");
    private static final Pattern SINGLE_YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");

    private static final Pattern DEGREE = Pattern.compile(
            "(?i)\\b(bachelor|master|b\\.?\\s?sc|m\\.?\\s?sc|b\\.?\\s?e\\b|m\\.?\\s?e\\b|b\\.?\\s?tech|m\\.?\\s?tech|bit\\b|bca\\b"
                    + "|bba\\b|mba\\b|mca\\b|ph\\.?\\s?d|diploma|\\+2|higher secondary|high school|a[ -]levels?|slc\\b|see\\b"
                    + "|associate degree|certificate in)");
    private static final Pattern INSTITUTION = Pattern.compile(
            "(?i)\\b(university|college|campus|school|institute|academy|polytechnic)\\b");
    private static final Pattern ROLE_COMPANY = Pattern.compile("^(.+?)(?:\\s*[,|]\\s+|\\s+(?:at|@|-|–| - )\\s+)(.+)$");

    private static final Map<CvSectionType, List<String>> HEADINGS = headings();

    /** PDF text often carries these where the author typed a space. */
    private static final char NO_BREAK_SPACE = (char) 0xA0;

    private final ObjectMapper objectMapper;
    private final KeywordVocabulary vocabulary;

    /**
     * What the parser found.
     *
     * @param document        v1 content document, ready for {@code POST /api/cvs}
     * @param sectionsFound   section types a heading was recognised for
     */
    public record Parsed(ObjectNode document, Set<CvSectionType> sectionsFound) {
    }

    public Parsed parse(String text) {
        List<String> lines = text.lines()
                .map(line -> line.replace(NO_BREAK_SPACE, ' ').replace("\t", "   ").strip())
                .toList();

        Map<CvSectionType, List<String>> sections = new EnumMap<>(CvSectionType.class);
        List<String> header = new ArrayList<>();
        List<String> current = header;
        for (String line : lines) {
            Optional<CvSectionType> heading = heading(line);
            // "Languages: Java, Kotlin" inside a skills block is a skill group, not a new section.
            boolean skillGroup = current == sections.get(CvSectionType.SKILLS) && !afterColon(line).isBlank();
            if (heading.isPresent() && !skillGroup) {
                current = sections.computeIfAbsent(heading.get(), type -> new ArrayList<>());
                // A heading such as "Skills: Java, React" carries content on the same line.
                String rest = afterColon(line);
                if (!rest.isBlank()) {
                    current.add(rest);
                }
                continue;
            }
            current.add(line);
        }

        ObjectNode document = objectMapper.createObjectNode();
        document.put("schemaVersion", CvContentValidator.SCHEMA_VERSION);
        header(document.putObject("basics"), header);

        ArrayNode out = document.putArray("sections");
        summary(out, sections.get(CvSectionType.SUMMARY));
        experience(out, sections.get(CvSectionType.EXPERIENCE));
        education(out, sections.get(CvSectionType.EDUCATION));
        skills(out, sections.get(CvSectionType.SKILLS));
        projects(out, sections.get(CvSectionType.PROJECTS));
        languages(out, sections.get(CvSectionType.LANGUAGES));

        return new Parsed(document, sections.keySet());
    }

    // --- Headings

    private static Map<CvSectionType, List<String>> headings() {
        Map<CvSectionType, List<String>> map = new EnumMap<>(CvSectionType.class);
        map.put(CvSectionType.SUMMARY, List.of("summary", "professional summary", "profile", "professional profile",
                "about me", "about", "objective", "career objective", "career summary", "personal statement"));
        map.put(CvSectionType.EXPERIENCE, List.of("experience", "work experience", "professional experience",
                "employment", "employment history", "work history", "career history", "relevant experience",
                "internships", "internship", "internship experience"));
        map.put(CvSectionType.EDUCATION, List.of("education", "academic background", "academic qualifications",
                "qualifications", "educational background", "academics", "education and training"));
        map.put(CvSectionType.SKILLS, List.of("skills", "technical skills", "key skills", "core skills",
                "core competencies", "competencies", "technologies", "tech stack", "skills and tools",
                "tools and technologies", "soft skills", "skill set", "skillset"));
        map.put(CvSectionType.PROJECTS, List.of("projects", "personal projects", "academic projects",
                "key projects", "selected projects", "side projects", "project experience"));
        map.put(CvSectionType.LANGUAGES, List.of("languages", "language", "language skills", "languages known"));
        map.put(CvSectionType.CERTIFICATIONS, List.of("certifications", "certificates", "certification",
                "licenses and certifications", "courses", "training", "trainings"));
        map.put(CvSectionType.CUSTOM, List.of("references", "hobbies", "interests", "hobbies and interests",
                "achievements", "awards", "awards and achievements", "extracurricular activities",
                "volunteering", "volunteer experience", "declaration", "personal details", "personal information"));
        return map;
    }

    /** A short line whose words, minus a trailing colon, name a section. */
    static Optional<CvSectionType> heading(String line) {
        if (line.isEmpty() || line.length() > 45) {
            return Optional.empty();
        }
        String candidate = line.contains(":") ? line.substring(0, line.indexOf(':')) : line;
        String normalized = candidate.toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<CvSectionType, List<String>> entry : HEADINGS.entrySet()) {
            if (entry.getValue().contains(normalized)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static String afterColon(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1).strip();
    }

    // --- Header

    private void header(ObjectNode basics, List<String> lines) {
        String email = "";
        String phone = "";
        List<String> links = new ArrayList<>();
        String location = "";
        List<String> plain = new ArrayList<>();

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            boolean contactLine = false;
            // Not split on commas: "Kathmandu, Nepal" is one location.
            for (String part : line.split("\\s*[|•·]\\s*|\\s{3,}")) {
                String piece = part.strip();
                if (piece.isEmpty()) {
                    continue;
                }
                Matcher mail = EMAIL.matcher(piece);
                if (mail.find()) {
                    email = email.isEmpty() ? mail.group() : email;
                    contactLine = true;
                    continue;
                }
                Matcher tel = PHONE.matcher(piece);
                if (tel.find() && tel.group(1).replaceAll("\\D", "").length() >= 7) {
                    phone = phone.isEmpty() ? tel.group(1).strip() : phone;
                    contactLine = true;
                    continue;
                }
                Matcher url = URL.matcher(piece);
                if (url.find() && url.group(1).contains(".") && !piece.contains(" ")) {
                    links.add(url.group(1));
                    contactLine = true;
                    continue;
                }
                if (location.isEmpty() && looksLikeLocation(piece)) {
                    location = piece;
                    contactLine = true;
                }
            }
            if (!contactLine) {
                plain.add(line);
            }
        }

        String fullName = plain.stream().filter(CvTextParser::looksLikeName).findFirst().orElse("");
        String headline = plain.stream()
                .filter(line -> !line.equals(fullName))
                .filter(line -> line.length() <= 80)
                .findFirst()
                .orElse("");

        basics.put("fullName", fullName);
        basics.put("headline", headline);
        basics.put("email", email);
        basics.put("phone", phone);
        basics.put("location", location);
        ArrayNode linkArray = basics.putArray("links");
        for (String link : new LinkedHashSet<>(links)) {
            String lower = link.toLowerCase(Locale.ROOT);
            String label = lower.contains("linkedin.") ? "LinkedIn"
                    : lower.contains("github.") ? "GitHub"
                    : "Website";
            linkArray.addObject().put("label", label).put("url", link);
        }
    }

    private static boolean looksLikeName(String line) {
        String[] words = line.strip().split("\\s+");
        return words.length >= 2 && words.length <= 5
                && line.length() <= 50
                && line.chars().noneMatch(Character::isDigit)
                && line.chars().filter(Character::isLetter).count() >= line.replace(" ", "").length() - 2;
    }

    /** "Kathmandu, Nepal", "Lalitpur" next to other contact details - short, lettered, no digits. */
    private static boolean looksLikeLocation(String piece) {
        return piece.length() <= 40
                && piece.chars().noneMatch(Character::isDigit)
                && (piece.toLowerCase(Locale.ROOT).contains("nepal") || piece.matches("[A-Z][a-zA-Z .'-]+,\\s*[A-Z][a-zA-Z .'-]+"));
    }

    // --- Sections

    private void summary(ArrayNode out, List<String> lines) {
        String text = paragraph(lines);
        if (!text.isBlank()) {
            section(out, CvSectionType.SUMMARY, "Summary").addObject()
                    .put("id", id())
                    .put("text", text);
        }
    }

    private void experience(ArrayNode out, List<String> lines) {
        List<Entry> entries = entries(lines);
        if (entries.isEmpty()) {
            return;
        }
        ArrayNode items = section(out, CvSectionType.EXPERIENCE, "Experience");
        for (Entry entry : entries) {
            String role = entry.titles().isEmpty() ? "" : entry.titles().get(0);
            String company = entry.titles().size() > 1 ? entry.titles().get(1) : "";
            if (company.isEmpty()) {
                Matcher split = ROLE_COMPANY.matcher(role);
                if (split.matches()) {
                    role = split.group(1).strip();
                    company = split.group(2).strip();
                }
            }
            items.addObject()
                    .put("id", id())
                    .put("company", company)
                    .put("role", role)
                    .put("startDate", entry.start())
                    .put("endDate", entry.current() ? "" : entry.end())
                    .put("current", entry.current())
                    .put("description", String.join("\n", entry.body()));
        }
    }

    private void education(ArrayNode out, List<String> lines) {
        List<Entry> entries = entries(lines);
        if (entries.isEmpty()) {
            return;
        }
        ArrayNode items = section(out, CvSectionType.EDUCATION, "Education");
        for (Entry entry : entries) {
            List<String> titles = new ArrayList<>(entry.titles());
            String degree = titles.stream().filter(line -> DEGREE.matcher(line).find()).findFirst().orElse("");
            String institution = titles.stream()
                    .filter(line -> !line.equals(degree) && INSTITUTION.matcher(line).find())
                    .findFirst()
                    .orElse("");
            titles.remove(degree);
            titles.remove(institution);
            // No keyword matched: fall back to reading order, degree first.
            String finalDegree = degree.isEmpty() && !titles.isEmpty() ? titles.remove(0) : degree;
            String finalInstitution = institution.isEmpty() && !titles.isEmpty() ? titles.remove(0) : institution;
            List<String> body = new ArrayList<>(titles);
            body.addAll(entry.body());
            items.addObject()
                    .put("id", id())
                    .put("institution", finalInstitution)
                    .put("degree", finalDegree)
                    .put("startDate", yearOf(entry.start()))
                    .put("endDate", entry.current() ? "" : yearOf(entry.end()))
                    .put("description", String.join("\n", body));
        }
    }

    private void skills(ArrayNode out, List<String> lines) {
        if (lines == null) {
            return;
        }
        Set<String> technical = new LinkedHashSet<>();
        Set<String> soft = new LinkedHashSet<>();
        for (String line : lines) {
            // "Languages: Java, Go" - the label is a grouping, not a skill.
            String content = line.contains(":") && line.indexOf(':') < 30 ? afterColon(line) : line;
            for (String token : content.split("\\s*[,;|•·]\\s*|\\s{2,}")) {
                String skill = BULLET.matcher(token).replaceFirst("").strip();
                if (skill.isEmpty() || skill.length() > 40) {
                    continue;
                }
                boolean isSoft = vocabulary.find(skill).stream().anyMatch(KeywordVocabulary.Term::soft);
                (isSoft ? soft : technical).add(skill);
            }
        }
        if (technical.isEmpty() && soft.isEmpty()) {
            return;
        }
        ArrayNode items = section(out, CvSectionType.SKILLS, "Skills");
        if (!technical.isEmpty()) {
            ArrayNode keywords = items.addObject().put("id", id()).put("name", "Technical").putArray("keywords");
            technical.forEach(keywords::add);
        }
        if (!soft.isEmpty()) {
            ArrayNode keywords = items.addObject().put("id", id()).put("name", "Soft").putArray("keywords");
            soft.forEach(keywords::add);
        }
    }

    /** A new project starts at every non-bullet line that follows a bullet or a blank line. */
    private void projects(ArrayNode out, List<String> lines) {
        if (lines == null) {
            return;
        }
        List<List<String>> blocks = new ArrayList<>();
        List<String> block = null;
        boolean boundary = true;
        for (String line : lines) {
            if (line.isBlank()) {
                boundary = true;
                continue;
            }
            boolean bullet = BULLET.matcher(line).find();
            if (!bullet && (boundary || block == null)) {
                block = new ArrayList<>();
                blocks.add(block);
            }
            if (block == null) {
                block = new ArrayList<>();
                blocks.add(block);
            }
            block.add(line);
            boundary = bullet;
        }
        if (blocks.isEmpty()) {
            return;
        }
        ArrayNode items = section(out, CvSectionType.PROJECTS, "Projects");
        for (List<String> projectLines : blocks) {
            String title = BULLET.matcher(projectLines.get(0)).replaceFirst("");
            String url = "";
            Matcher link = URL.matcher(String.join(" ", projectLines));
            while (link.find()) {
                if (link.group(1).contains("/") || link.group(1).startsWith("http")) {
                    url = link.group(1);
                    break;
                }
            }
            if (!url.isEmpty()) {
                title = title.replace(url, "").replaceAll("[\\s|:\u2013\u2014-]+$", "").strip();
            }
            items.addObject()
                    .put("id", id())
                    .put("name", title)
                    .put("url", url)
                    .put("description", String.join("\n", clean(projectLines.subList(1, projectLines.size()))));
        }
    }

    private void languages(ArrayNode out, List<String> lines) {
        if (lines == null) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (String line : lines) {
            for (String token : line.split("\\s*[,;|•·]\\s*")) {
                String name = BULLET.matcher(token).replaceFirst("").strip();
                if (!name.isEmpty() && name.length() <= 40) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty()) {
            return;
        }
        ArrayNode items = section(out, CvSectionType.LANGUAGES, "Languages");
        names.forEach(name -> items.addObject().put("id", id()).put("name", name));
    }

    // --- Dated entries (experience, education)

    /**
     * @param titles up to two non-bullet lines naming the entry (role and company, degree and school)
     * @param body   everything else, bullets stripped
     */
    private record Entry(List<String> titles, String start, String end, boolean current, List<String> body) {
    }

    /**
     * Every line holding a date range (or, failing that, a lone year) anchors an entry. The one or
     * two short, non-bullet lines right above it are the entry's titles; the rest of the text on
     * the date line is a title too ("Backend Engineer, Fonepay   Jan 2022 – Present").
     */
    private List<Entry> entries(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        List<String> content = lines.stream().filter(line -> !line.isBlank()).toList();

        List<Integer> anchors = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            if (DATE_RANGE.matcher(content.get(i)).find()) {
                anchors.add(i);
            }
        }
        if (anchors.isEmpty()) {
            for (int i = 0; i < content.size(); i++) {
                if (!BULLET.matcher(content.get(i)).find() && SINGLE_YEAR.matcher(content.get(i)).find()) {
                    anchors.add(i);
                }
            }
        }
        if (anchors.isEmpty()) {
            if (content.isEmpty()) {
                return List.of();
            }
            List<String> titles = content.subList(0, Math.min(2, content.size()));
            List<String> body = content.subList(titles.size(), content.size());
            return List.of(new Entry(clean(titles), "", "", false, clean(body)));
        }

        // Where each entry's title lines begin: up to two short non-bullet lines above the anchor,
        // never reaching back past the previous anchor.
        int[] starts = new int[anchors.size()];
        for (int a = 0; a < anchors.size(); a++) {
            int anchor = anchors.get(a);
            int floor = a == 0 ? 0 : anchors.get(a - 1) + 1;
            int start = anchor;
            while (start > floor && anchor - start < 2) {
                String above = content.get(start - 1);
                if (BULLET.matcher(above).find() || above.length() > 90 || above.endsWith(".")) {
                    break;
                }
                start--;
            }
            starts[a] = a == 0 ? Math.min(start, anchor) : start;
        }

        List<Entry> entries = new ArrayList<>();
        for (int a = 0; a < anchors.size(); a++) {
            int anchor = anchors.get(a);
            int end = a + 1 < anchors.size() ? starts[a + 1] : content.size();

            List<String> titles = new ArrayList<>(content.subList(starts[a], anchor));
            String dateLine = content.get(anchor);
            String start = "";
            String finish = "";
            boolean current = false;

            Matcher range = DATE_RANGE.matcher(dateLine);
            if (range.find()) {
                start = normalizeDate(range.group(1));
                current = range.group(2).matches("(?i)" + NOW);
                finish = current ? "" : normalizeDate(range.group(2));
                dateLine = (dateLine.substring(0, range.start()) + " " + dateLine.substring(range.end())).strip();
            } else {
                Matcher year = SINGLE_YEAR.matcher(dateLine);
                if (year.find()) {
                    finish = year.group(1);
                    dateLine = (dateLine.substring(0, year.start()) + " " + dateLine.substring(year.end())).strip();
                }
            }
            dateLine = dateLine.replaceAll("^[\\s|,()\u2013\u2014-]+|[\\s|,()\u2013\u2014-]+$", "");
            if (!dateLine.isBlank()) {
                titles.add(dateLine);
            }

            List<String> body = new ArrayList<>(content.subList(anchor + 1, end));
            // Before the first anchor: anything above the titles belongs to nothing - keep it as body
            // of the first entry rather than dropping text the user wrote.
            if (a == 0 && starts[0] > 0) {
                body.addAll(0, content.subList(0, starts[0]));
            }
            // A third title line is description, not a name.
            while (titles.size() > 2) {
                body.add(0, titles.remove(titles.size() - 1));
            }
            entries.add(new Entry(clean(titles), start, finish, current, clean(body)));
        }
        return entries;
    }

    /** To the editor's {@code YYYY-MM}, or a bare {@code YYYY} when that is all there is. */
    static String normalizeDate(String value) {
        String trimmed = value.strip().toLowerCase(Locale.ROOT).replace(".", "");
        if (trimmed.matches("\\d{4}-\\d{2}")) {
            return trimmed;
        }
        Matcher slash = Pattern.compile("(\\d{1,2})/(\\d{4})").matcher(trimmed);
        if (slash.matches()) {
            return slash.group(2) + "-" + String.format("%02d", Integer.parseInt(slash.group(1)));
        }
        Matcher named = Pattern.compile("([a-z]+)\\s+(\\d{4})").matcher(trimmed);
        if (named.matches()) {
            String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
            for (int i = 0; i < months.length; i++) {
                if (named.group(1).startsWith(months[i])) {
                    return named.group(2) + "-" + String.format("%02d", i + 1);
                }
            }
        }
        Matcher year = SINGLE_YEAR.matcher(trimmed);
        return year.find() ? year.group(1) : "";
    }

    private static String yearOf(String date) {
        return date.length() >= 4 ? date.substring(0, 4) : date;
    }

    // --- Helpers

    private ArrayNode section(ArrayNode sections, CvSectionType type, String title) {
        ObjectNode section = sections.addObject();
        section.put("id", id());
        section.put("type", type.name());
        section.put("title", title);
        section.put("visible", true);
        return section.putArray("items");
    }

    /** Bullets stripped, blanks dropped. */
    private static List<String> clean(List<String> lines) {
        return lines.stream()
                .map(line -> BULLET.matcher(line).replaceFirst("").strip())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /** Lines joined back into prose; a hard-wrapped PDF paragraph becomes one paragraph again. */
    private static String paragraph(List<String> lines) {
        if (lines == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) {
                if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                out.append(' ');
            }
            out.append(BULLET.matcher(line).replaceFirst(""));
        }
        return out.toString().strip();
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
