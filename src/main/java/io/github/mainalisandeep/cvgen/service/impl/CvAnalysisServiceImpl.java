package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.dto.CvAnalysisRequestDto;
import io.github.mainalisandeep.cvgen.dto.CvAnalysisResponseDto;
import io.github.mainalisandeep.cvgen.entity.Cv;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import io.github.mainalisandeep.cvgen.records.RenderedCv;
import io.github.mainalisandeep.cvgen.repository.CvRepository;
import io.github.mainalisandeep.cvgen.service.CvAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword match between a stored CV and a pasted job description.
 * <p>
 * Deliberately a transparent heuristic, not a model: terms come from a curated vocabulary plus
 * the acronyms the posting itself uses, and a term counts as covered when the CV text contains
 * it. Every number in the result can be traced back to the lists it is computed from, which is
 * what the product promises ("keyword coverage, not a vanity score").
 * <p>
 * Nothing is stored. The CV is read through its owner like every other CV endpoint.
 */
@Service
@RequiredArgsConstructor
public class CvAnalysisServiceImpl implements CvAnalysisService {

    private static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_EXTRA_TERMS = 15;

    /** Lines that turn what follows, or themselves, into nice-to-haves. */
    private static final Pattern OPTIONAL_MARKER = Pattern.compile(
            "nice[ -]to[ -]have|good[ -]to[ -]have|\\bpreferred\\b|\\bbonus\\b|\\bis a plus\\b|\\ba plus\\b"
                    + "|\\boptional\\b|\\bdesirable\\b|familiarity with",
            Pattern.CASE_INSENSITIVE);

    /** A short line ending in a colon, e.g. "Requirements:". Resets the optional block. */
    private static final Pattern HEADING = Pattern.compile("^[^.]{2,60}:\\s*$");

    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?(?:years?|yrs?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ACRONYM = Pattern.compile("(?<![A-Za-z0-9])[A-Z][A-Z0-9]{1,5}(?![A-Za-z0-9])");

    /** Capitals a posting uses that are not skills. */
    private static final Set<String> NOT_SKILLS = Set.of(
            "CV", "US", "USA", "UK", "EU", "HR", "IT", "NPR", "USD", "OR", "AND", "TO", "WE", "NA", "EOE",
            "PVT", "LTD", "INC", "LLC", "CEO", "CTO", "CFO", "COO", "VP", "ASAP", "FAQ", "ID", "OK", "AM",
            "PM", "KTM", "NOTE", "JD", "TBD", "ETA", "FTE", "WFH", "BS", "BSC", "MSC", "BE", "BIT", "BCA",
            "MBA", "BBA", "MCA", "PHD", "GPA", "CGPA", "SLC", "SEE", "NEB", "TU", "KU", "PU", "IOE", "IST",
            "NST", "UTC", "GMT", "AI", "API", "APIS"
    );

    private static final Set<CvSectionType> ALL_SECTIONS = EnumSet.allOf(CvSectionType.class);

    private final CvRepository cvRepository;
    private final CvDocumentReader cvDocumentReader;
    private final KeywordVocabulary vocabulary;
    private final CustomMessageSource messages;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CvAnalysisResponseDto analyze(UUID userId, UUID cvId, CvAnalysisRequestDto request) {
        Cv cv = cvRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FieldConstantValue.CV));

        RenderedCv rendered = cvDocumentReader.read(cv.getContent(), ALL_SECTIONS, "");
        String cvText = cvDocumentReader.plainText(rendered);
        String jobDescription = request.getJobDescription();

        Map<String, Requirement> requirements = requirements(jobDescription);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        int hardTotal = 0;
        int hardMatched = 0;

        for (Requirement requirement : requirements.values()) {
            boolean covered = requirement.coveredBy(cvText);
            if (!requirement.optional()) {
                if (requirement.hardSkill()) {
                    hardTotal++;
                    hardMatched += covered ? 1 : 0;
                }
            }
            if (covered) {
                matched.add(requirement.name());
            } else if (requirement.optional()) {
                optional.add(requirement.name());
            } else {
                missing.add(requirement.name());
            }
        }

        int counted = matched.size() + missing.size();
        int coverage = counted == 0 ? 0 : percent(matched.size(), counted);
        int skills = hardTotal == 0 ? coverage : percent(hardMatched, hardTotal);

        Integer requiredYears = requiredYears(jobDescription);
        double cvYears = experienceYears(cv.getContent());
        int experience = experienceScore(requiredYears, cvYears, section(rendered, CvSectionType.EXPERIENCE));

        List<CvAnalysisResponseDto.Check> checks = checks(cv.getContent(), rendered, request.getJobTitle());
        List<String> suggestions = suggestions(requirements, missing, checks, request.getJobTitle());
        int failed = (int) checks.stream().filter(check -> !check.passed()).count();

        return new CvAnalysisResponseDto(
                coverage,
                matched,
                missing,
                optional,
                new CvAnalysisResponseDto.Categories(skills, coverage, experience),
                checks,
                suggestions,
                new CvAnalysisResponseDto.Counts(matched.size(), missing.size(), failed),
                requiredYears,
                cvYears
        );
    }

    /**
     * One term the posting asks for.
     *
     * @param hardSkill a vocabulary term that is not a soft skill - feeds the skills bar
     * @param optional  only ever mentioned in nice-to-have lines
     */
    private record Requirement(String name, boolean hardSkill, boolean optional, KeywordVocabulary.Term term,
                               Pattern literal) {

        boolean coveredBy(String text) {
            return term != null ? term.foundIn(text) : literal.matcher(text).find();
        }

        Requirement required() {
            return new Requirement(name, hardSkill, false, term, literal);
        }
    }

    /**
     * Terms in posting order. A term seen in any required line is required, even if a later
     * nice-to-have line repeats it.
     */
    private Map<String, Requirement> requirements(String jobDescription) {
        Map<String, Requirement> found = new LinkedHashMap<>();
        boolean optionalBlock = false;
        int extras = 0;

        for (String line : jobDescription.split("\\R|(?<=[.;!?])\\s+")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            boolean marked = OPTIONAL_MARKER.matcher(trimmed).find();
            if (HEADING.matcher(trimmed).matches()) {
                optionalBlock = marked;
            }
            boolean optional = optionalBlock || marked;

            for (KeywordVocabulary.Term term : vocabulary.find(trimmed)) {
                String key = term.name().toLowerCase(Locale.ROOT);
                add(found, key, new Requirement(term.name(), !term.soft(), optional, term, null));
            }

            Matcher acronyms = ACRONYM.matcher(vocabulary.mask(trimmed));
            while (acronyms.find() && extras < MAX_EXTRA_TERMS) {
                String token = acronyms.group();
                String key = token.toLowerCase(Locale.ROOT);
                if (NOT_SKILLS.contains(token) || found.containsKey(key) || vocabulary.contains(token)) {
                    continue;
                }
                Pattern literal = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(token) + "(?![A-Za-z0-9])");
                found.put(key, new Requirement(token, false, optional, null, literal));
                extras++;
            }
        }
        return found;
    }

    private static void add(Map<String, Requirement> found, String key, Requirement requirement) {
        Requirement existing = found.get(key);
        if (existing == null) {
            found.put(key, requirement);
        } else if (existing.optional() && !requirement.optional()) {
            found.put(key, existing.required());
        }
    }

    /** The first "N years" / "N+ yrs" in the posting, capped at 30 so a founding year is not read as one. */
    static Integer requiredYears(String jobDescription) {
        Matcher matcher = YEARS.matcher(jobDescription);
        while (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            if (years > 0 && years <= 30) {
                return years;
            }
        }
        return null;
    }

    /** Months covered by experience dates, overlapping jobs counted once, in years to one decimal. */
    double experienceYears(JsonNode document) {
        YearMonth now = YearMonth.now(clock);
        List<YearMonth[]> spans = new ArrayList<>();

        for (JsonNode section : iterable(document == null ? null : document.get("sections"))) {
            if (!CvSectionType.EXPERIENCE.name().equals(section.path("type").asText())) {
                continue;
            }
            for (JsonNode item : iterable(section.get("items"))) {
                Optional<YearMonth> start = yearMonth(item.path("startDate").asText(""), false);
                if (start.isEmpty()) {
                    continue;
                }
                YearMonth end = item.path("current").asBoolean(false)
                        ? now
                        : yearMonth(item.path("endDate").asText(""), true).orElse(now);
                if (end.isAfter(now)) {
                    end = now;
                }
                if (!end.isBefore(start.get())) {
                    spans.add(new YearMonth[]{start.get(), end});
                }
            }
        }

        spans.sort(Comparator.comparing((YearMonth[] span) -> span[0]));
        long months = 0;
        YearMonth[] current = null;
        for (YearMonth[] span : spans) {
            if (current == null || span[0].isAfter(current[1])) {
                if (current != null) {
                    months += monthsBetween(current);
                }
                current = span.clone();
            } else if (span[1].isAfter(current[1])) {
                current[1] = span[1];
            }
        }
        if (current != null) {
            months += monthsBetween(current);
        }
        return Math.round(months / 12.0 * 10) / 10.0;
    }

    private static long monthsBetween(YearMonth[] span) {
        return span[0].until(span[1], java.time.temporal.ChronoUnit.MONTHS) + 1;
    }

    /** {@code 2024-03} or a bare {@code 2024}; a bare year means January as a start and December as an end. */
    private static Optional<YearMonth> yearMonth(String value, boolean end) {
        String trimmed = value.strip();
        try {
            if (trimmed.matches("\\d{4}-\\d{2}")) {
                return Optional.of(YearMonth.parse(trimmed));
            }
            if (trimmed.matches("\\d{4}")) {
                return Optional.of(YearMonth.of(Integer.parseInt(trimmed), end ? 12 : 1));
            }
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static int experienceScore(Integer requiredYears, double cvYears, Optional<RenderedCv.Section> experience) {
        int entries = experience.map(section -> section.items().size()).orElse(0);
        if (requiredYears != null) {
            return Math.min(100, (int) Math.round(cvYears / requiredYears * 100));
        }
        return entries == 0 ? 0 : Math.min(100, 50 + entries * 25);
    }

    private List<CvAnalysisResponseDto.Check> checks(JsonNode document, RenderedCv cv, String jobTitle) {
        List<CvAnalysisResponseDto.Check> checks = new ArrayList<>();
        JsonNode basics = document == null ? null : document.get("basics");

        failIf(checks, "MISSING_SUMMARY", section(cv, CvSectionType.SUMMARY).isEmpty());
        failIf(checks, "MISSING_SKILLS", section(cv, CvSectionType.SKILLS).isEmpty());

        Optional<RenderedCv.Section> experience = section(cv, CvSectionType.EXPERIENCE);
        failIf(checks, "MISSING_EXPERIENCE", experience.isEmpty());
        experience.ifPresent(section -> {
            failIf(checks, "UNDATED_EXPERIENCE", section.items().stream().anyMatch(item -> item.dates().isBlank()));
            failIf(checks, "NO_METRICS", section.items().stream().noneMatch(item -> item.body().matches("(?s).*\\d.*")));
        });

        boolean noEmail = basics == null || basics.path("email").asText("").isBlank();
        boolean noPhone = basics == null || basics.path("phone").asText("").isBlank();
        failIf(checks, "MISSING_CONTACT", noEmail || noPhone);

        if (jobTitle != null && !jobTitle.isBlank()) {
            failIf(checks, "HEADLINE_MISMATCH", !sharesWord(cv.headline(), jobTitle), jobTitle.strip());
        }

        // Every layout this server draws is single column with real text, so this always passes.
        // It is still listed: the user is told what was checked, not only what failed.
        checks.add(check("SINGLE_COLUMN", true));
        return checks;
    }

    private void failIf(List<CvAnalysisResponseDto.Check> checks, String code, boolean failed, Object... arguments) {
        if (failed) {
            checks.add(check(code, false, arguments));
        }
    }

    private CvAnalysisResponseDto.Check check(String code, boolean passed, Object... arguments) {
        String key = "analysis.check." + code.toLowerCase(Locale.ROOT).replace('_', '.');
        return new CvAnalysisResponseDto.Check(code, passed, messages.get(key, arguments));
    }

    private List<String> suggestions(Map<String, Requirement> requirements, List<String> missing,
                                     List<CvAnalysisResponseDto.Check> checks, String jobTitle) {
        List<String> suggestions = new ArrayList<>();
        boolean headlineMismatch = checks.stream().anyMatch(check -> "HEADLINE_MISMATCH".equals(check.code()));
        if (headlineMismatch) {
            suggestions.add(messages.get("analysis.suggestion.headline", jobTitle.strip()));
        }
        for (String term : missing) {
            if (suggestions.size() >= MAX_SUGGESTIONS) {
                break;
            }
            boolean hardSkill = requirements.get(term.toLowerCase(Locale.ROOT)).hardSkill();
            suggestions.add(messages.get(hardSkill ? "analysis.suggestion.skill" : "analysis.suggestion.mention", term));
        }
        return suggestions;
    }

    private static boolean sharesWord(String headline, String jobTitle) {
        Set<String> generic = Set.of("senior", "junior", "lead", "mid", "level", "associate", "intern", "the", "and", "of");
        List<String> headlineWords = words(headline);
        return words(jobTitle).stream()
                .filter(word -> word.length() > 2 && !generic.contains(word))
                .anyMatch(headlineWords::contains);
    }

    private static List<String> words(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+"))
                .filter(word -> !word.isBlank())
                .toList();
    }

    private static Optional<RenderedCv.Section> section(RenderedCv cv, CvSectionType type) {
        return cv.sections().stream().filter(section -> section.type().equals(type.name())).findFirst();
    }

    private static int percent(int part, int whole) {
        return (int) Math.round(part * 100.0 / whole);
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> objects = new ArrayList<>();
        node.forEach(child -> {
            if (child.isObject()) {
                objects.add(child);
            }
        });
        return objects;
    }
}
