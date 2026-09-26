package io.github.mainalisandeep.cvgen.records;

/**
 * A CV drawn as a complete XHTML page, ready for the PDF renderer.
 *
 * @param html     well-formed XHTML with every user value already escaped
 * @param fileName download name ending in {@code .pdf}, ASCII only so no header encoding is needed
 */
public record RenderedHtml(String html, String fileName) {
}
