package io.github.mainalisandeep.cvgen.records;

import java.util.List;

/**
 * A CV flattened into exactly what a layout draws: strings and lists, no JSON, no decisions.
 * <p>
 * Built from the stored content document by {@code CvDocumentReader}, so every template reads
 * the same shape and none of them walks a {@code JsonNode}. Everything here is plain text; the
 * template engine escapes it on the way out, which is what keeps user content from ever
 * becoming markup.
 *
 * @param fullName    candidate name, blank when unset
 * @param headline    professional title under the name
 * @param contact     email, phone, location and links in display order, blanks dropped
 * @param sections    visible sections the template supports, in document order
 * @param accentColor {@code #rrggbb} from the template row, never user input
 */
public record RenderedCv(
        String fullName,
        String headline,
        List<String> contact,
        List<Section> sections,
        String accentColor
) {

    /**
     * @param type  {@code CvSectionType} name, which picks the fragment the layout uses
     * @param title heading as the user wrote it
     */
    public record Section(String type, String title, List<Item> items) {
    }

    /**
     * One entry, whatever section it came from. Unused fields are blank rather than null so
     * templates need no null checks.
     *
     * @param heading    role, degree, project name or skill group
     * @param subheading company or institution
     * @param dates      already formatted, e.g. {@code Jan 2024 – Present}
     * @param url        only ever {@code http(s)}; anything else is dropped before it gets here
     * @param body       free text, line breaks preserved by the stylesheet
     * @param keywords   skills, empty for every other section
     */
    public record Item(
            String heading,
            String subheading,
            String dates,
            String url,
            String body,
            List<String> keywords
    ) {

        /**
         * {@link #body()} split into its non-blank lines, leading bullet characters dropped. Several
         * lines draw as a bullet list, one as a paragraph - the same rule as the editor's preview.
         */
        public List<String> lines() {
            return body.lines()
                    .map(line -> line.replaceFirst("^\\s*[-•*]\\s*", "").strip())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }
}
