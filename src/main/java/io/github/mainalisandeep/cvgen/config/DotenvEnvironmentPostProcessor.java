package io.github.mainalisandeep.cvgen.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
    private static final String DOTENV_FILE_NAME = ".env";
    private static final int HIGHEST_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String userDir = System.getProperty("user.dir");
        Path dotenvPath = Paths.get(userDir, DOTENV_FILE_NAME);
        String absolutePath = dotenvPath.toAbsolutePath().toString();

        log.info("Resolving .env file at: {}", absolutePath);

        Resource resource = new FileSystemResource(dotenvPath);

        if (!resource.exists()) {
            handleMissingEnvFile(environment, absolutePath);
            return;
        }

        try {
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            Properties properties = parseDotenv(content);

            if (!properties.isEmpty()) {
                PropertiesPropertySource propertySource = new PropertiesPropertySource("dotenv", properties);
                environment.getPropertySources().addFirst(propertySource);
                log.info("Loaded {} properties from .env file", properties.size());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read .env file at " + absolutePath, e);
        }
    }

    private void handleMissingEnvFile(ConfigurableEnvironment environment, String absolutePath) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isDevProfile = activeProfiles.contains("dev") || activeProfiles.contains("local");

        if (isDevProfile) {
            log.warn(".env file not found at {}. Continuing since 'dev' or 'local' profile is active. " +
                    "Ensure required environment variables are set via other means (IDE, OS env vars).", absolutePath);
        } else {
            throw new IllegalStateException(
                    ".env file not found at " + absolutePath +
                    ". In non-local environments, a .env file is required. " +
                    "Either create the .env file or activate the 'dev' or 'local' profile."
            );
        }
    }

    /**
     * Parses .env file content. Rules:
     * - Ignores blank lines
     * - Ignores lines starting with #
     * - Strips surrounding single or double quotes from values
     * - Does NOT treat backslash as escape (literal backslashes)
     * - KEY=VALUE format, whitespace around = is not supported (raw key=value)
     */
    Properties parseDotenv(String content) {
        Properties properties = new Properties();
        if (content == null || content.isBlank()) {
            return properties;
        }

        String[] lines = content.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }

            String key = trimmed.substring(0, equalsIndex).trim();
            String value = trimmed.substring(equalsIndex + 1);

            // Strip surrounding quotes (both single and double)
            value = stripQuotes(value);

            if (!key.isEmpty()) {
                properties.setProperty(key, value);
            }
        }

        return properties;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\"' && last == '\"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
