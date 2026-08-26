package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.github.mainalisandeep.cvgen.records.BinaryContent;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

/**
 * Downloads a provider avatar so it can be copied into our own storage.
 * <p>
 * This is the only place the server dereferences a URL it did not construct, so the limits
 * below are the security boundary, not tuning: HTTPS only, redirects followed by hand and
 * re-validated at each hop, a declared image content type, and a byte ceiling enforced while
 * reading rather than trusting {@code Content-Length}.
 * <p>
 * The URL must come from a provider attribute map that we stored ourselves. A URL taken from
 * a request body must never reach this class - that turns it into an SSRF primitive.
 */
@Component
@RequiredArgsConstructor
public class RemoteImageFetcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteImageFetcher.class);

    private static final String HTTPS = "https";

    private final StorageProperties storageProperties;

    /**
     * @return the downloaded image, or empty when the URL is unusable or the fetch failed.
     * Callers treat a miss as "no picture", never as an error worth failing a login over.
     */
    public Optional<BinaryContent> fetch(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        long maxBytes = storageProperties.getUpload().getMaxProfilePictureBytes();
        String current = rawUrl;

        try {
            for (int hop = 0; hop <= storageProperties.getRemoteFetch().getMaxRedirects(); hop++) {
                URI uri = URI.create(current);
                if (!HTTPS.equalsIgnoreCase(uri.getScheme())) {
                    // Covers plain http as well as file:, gopher: and friends.
                    log.debug("Refusing non-HTTPS avatar URL: {}", current);
                    return Optional.empty();
                }

                HttpURLConnection connection = open(uri.toURL());
                int status = connection.getResponseCode();

                if (status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307
                        || status == 308) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (location == null || location.isBlank()) {
                        return Optional.empty();
                    }
                    // Resolved against the current URL so a relative Location still lands somewhere valid,
                    // then re-checked for scheme on the next pass.
                    current = uri.resolve(location).toString();
                    continue;
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    connection.disconnect();
                    return Optional.empty();
                }

                String contentType = normalizeContentType(connection.getContentType());
                if (!storageProperties.getUpload().getAllowedImageMimeTypes().contains(contentType)) {
                    log.debug("Refusing avatar with content type {}", contentType);
                    connection.disconnect();
                    return Optional.empty();
                }

                try (InputStream stream = connection.getInputStream()) {
                    byte[] bytes = readAtMost(stream, maxBytes);
                    if (bytes == null) {
                        log.debug("Avatar at {} exceeds {} bytes", current, maxBytes);
                        return Optional.empty();
                    }
                    return Optional.of(new BinaryContent(bytes, contentType, null));
                } finally {
                    connection.disconnect();
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            // A provider being slow or down is not this application's failure.
            log.debug("Avatar fetch failed for {}: {}", rawUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpURLConnection open(URL url) throws java.io.IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false); // handled above so each hop is re-validated
        connection.setConnectTimeout((int) storageProperties.getRemoteFetch().getConnectTimeout().toMillis());
        connection.setReadTimeout((int) storageProperties.getRemoteFetch().getReadTimeout().toMillis());
        connection.setRequestProperty("Accept", "image/*");
        return connection;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';'); // drop "; charset=..."
        String base = separator < 0 ? contentType : contentType.substring(0, separator);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns {@code null} once the stream proves larger than {@code maxBytes}, without buffering the rest. */
    private byte[] readAtMost(InputStream stream, long maxBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        long total = 0;
        while ((read = stream.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                return null;
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
