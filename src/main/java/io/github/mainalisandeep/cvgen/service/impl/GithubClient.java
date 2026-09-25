package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.exception.ResourceNotFoundException;
import io.github.mainalisandeep.cvgen.common.exception.ServiceUnavailableException;
import io.github.mainalisandeep.cvgen.common.exception.TooManyRequestsException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.message.FieldConstantValue;
import io.github.mainalisandeep.cvgen.config.GithubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.regex.Pattern;

/**
 * GitHub's public REST API, read-only.
 * <p>
 * The username is the only caller input and it is checked against GitHub's own username rules
 * before it goes anywhere near a URL, so it cannot add a path segment, a query or a host. The base
 * URL comes from configuration, never from a request.
 */
@Component
public class GithubClient {

    private static final Logger log = LoggerFactory.getLogger(GithubClient.class);

    /** GitHub's rule: 1-39 alphanumerics or single hyphens, not starting or ending with one. */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$");

    private final RestClient restClient;

    public GithubClient(GithubProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.getTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "CVGen");
        if (properties.getToken() != null && !properties.getToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken().strip());
        }
        this.restClient = builder.build();
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /** Up to 100 public repositories owned by the user, most recently pushed first. */
    public JsonNode ownedRepositories(String username) {
        if (!isValidUsername(username)) {
            throw new BadRequestException(ErrorConstantValue.GITHUB_USERNAME_INVALID);
        }
        try {
            return restClient.get()
                    .uri("/users/{username}/repos?type=owner&sort=pushed&per_page=100", username)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                throw ResourceNotFoundException.of(FieldConstantValue.GITHUB_USER);
            }
            if (status == HttpStatus.FORBIDDEN || status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new TooManyRequestsException(ErrorConstantValue.GITHUB_RATE_LIMITED);
            }
            log.warn("GitHub answered {} for repositories of {}", e.getStatusCode(), username);
            throw new ServiceUnavailableException(ErrorConstantValue.GITHUB_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("GitHub unreachable: {}", e.getMessage());
            throw new ServiceUnavailableException(ErrorConstantValue.GITHUB_UNAVAILABLE);
        }
    }
}
