package io.github.mainalisandeep.cvgen.records;

/**
 * Bytes in flight, before they become a {@code StoredFile}: an upload just read from the
 * request, or an avatar just fetched from a provider.
 *
 * @param bytes       raw content, already fully read and size-checked
 * @param contentType MIME type, validated against the configured allow-list
 * @param filename    original name where one exists, {@code null} for a remote fetch
 */
public record BinaryContent(byte[] bytes, String contentType, String filename) {
}
