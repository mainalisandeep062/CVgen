package io.github.mainalisandeep.cvgen.service.impl;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The terms match analysis knows, loaded once from {@code analysis/vocabulary.txt}.
 * <p>
 * Every alias compiles to a word-bounded pattern, so "Java" never matches inside "JavaScript"
 * and "Git" never inside "GitHub". Where two terms overlap in the same text ("Spring" inside
 * "Spring Boot") the longer one wins and the shorter is not counted from that span.
 */
@Component
public class KeywordVocabulary {

    private static final String SOURCE = "analysis/vocabulary.txt";

    private final List<Term> terms;

    public KeywordVocabulary() {
        this.terms = load();
    }

    /**
     * A recognised term.
     *
     * @param name     canonical spelling shown to the user
     * @param soft     a soft skill: counts for coverage, not for the skills-match bar
     * @param patterns one per alias, the canonical name included
     */
    public record Term(String name, boolean soft, List<Pattern> patterns) {

        /** Whether any alias of this term appears in {@code text}. */
        public boolean foundIn(String text) {
            return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
        }
    }

    /** Terms found in {@code text}, in order of first appearance, overlaps resolved longest-first. */
    public List<Term> find(String text) {
        Set<Term> ordered = new LinkedHashSet<>();
        hits(text).forEach(hit -> ordered.add(hit.term()));
        return List.copyOf(ordered);
    }

    /**
     * {@code text} with every recognised term blanked out, so a second pass looking for other
     * tokens does not pick "API" back out of "REST API".
     */
    public String mask(String text) {
        char[] chars = text.toCharArray();
        for (Hit hit : hits(text)) {
            for (int i = hit.start(); i < hit.end(); i++) {
                chars[i] = ' ';
            }
        }
        return new String(chars);
    }

    private record Hit(Term term, int start, int end) {
    }

    /** Accepted matches in text order; where two overlap, the longer one wins. */
    private List<Hit> hits(String text) {
        List<Hit> hits = new ArrayList<>();
        for (Term term : terms) {
            for (Pattern pattern : term.patterns()) {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    hits.add(new Hit(term, matcher.start(), matcher.end()));
                }
            }
        }

        hits.sort(Comparator.comparingInt((Hit hit) -> hit.end() - hit.start()).reversed());
        boolean[] taken = new boolean[text.length()];
        List<Hit> accepted = new ArrayList<>();
        for (Hit hit : hits) {
            boolean free = true;
            for (int i = hit.start(); i < hit.end() && free; i++) {
                free = !taken[i];
            }
            if (free) {
                for (int i = hit.start(); i < hit.end(); i++) {
                    taken[i] = true;
                }
                accepted.add(hit);
            }
        }

        accepted.sort(Comparator.comparingInt(Hit::start));
        return accepted;
    }

    /** Whether {@code name} is a known term, compared case-insensitively. */
    public boolean contains(String name) {
        return terms.stream().anyMatch(term -> term.name().equalsIgnoreCase(name));
    }

    private static List<Term> load() {
        List<Term> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                boolean soft = line.startsWith("~");
                String[] aliases = (soft ? line.substring(1) : line).split("\\|");
                List<Pattern> patterns = new ArrayList<>();
                for (String alias : aliases) {
                    patterns.add(pattern(alias.strip()));
                }
                loaded.add(new Term(strip(aliases[0].strip()), soft, List.copyOf(patterns)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Missing " + SOURCE, e);
        }
        return List.copyOf(loaded);
    }

    /**
     * Word-bounded on letters and digits only, so "C++", ".NET" and "Node.js." at the end of a
     * sentence still match. A leading {@code =} makes the alias case-sensitive.
     */
    private static Pattern pattern(String alias) {
        boolean caseSensitive = alias.startsWith("=");
        String literal = Pattern.quote(strip(alias));
        String regex = "(?<![A-Za-z0-9])" + literal + "(?![A-Za-z0-9+#])";
        return caseSensitive ? Pattern.compile(regex) : Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String strip(String alias) {
        return alias.startsWith("=") ? alias.substring(1) : alias;
    }
}
