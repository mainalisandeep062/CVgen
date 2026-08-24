package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fetcher is the only place the server dereferences a URL it did not construct, so these
 * cover the refusals rather than the happy path: each one is a hole that would otherwise be
 * reachable from stored provider data.
 */
class RemoteImageFetcherTest {

    private final RemoteImageFetcher fetcher = new RemoteImageFetcher(new StorageProperties());

    @Test
    @DisplayName("Plain http is refused without a request being made")
    void refusesPlainHttp() {
        assertThat(fetcher.fetch("http://example.com/avatar.jpg")).isEmpty();
    }

    @Test
    @DisplayName("Non-network schemes are refused: file: would read the server's own disk")
    void refusesFileScheme() {
        assertThat(fetcher.fetch("file:///etc/passwd")).isEmpty();
    }

    @Test
    @DisplayName("Missing and blank URLs are a miss, not a failure")
    void treatsAbsentUrlAsMiss() {
        assertThat(fetcher.fetch(null)).isEmpty();
        assertThat(fetcher.fetch("  ")).isEmpty();
    }

    @Test
    @DisplayName("An unresolvable host fails soft so a signup is never lost to a provider outage")
    void unreachableHostReturnsEmpty() {
        assertThat(fetcher.fetch("https://this-host-does-not-resolve.invalid/a.png")).isEmpty();
    }
}
