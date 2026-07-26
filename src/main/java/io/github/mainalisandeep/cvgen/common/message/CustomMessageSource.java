package io.github.mainalisandeep.cvgen.common.message;

import io.github.mainalisandeep.cvgen.common.locale.LocaleThreadStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Single entry point for resolving user-facing text from {@code messages.properties}
 * against the locale bound to the current request.
 * <p>
 * An unknown key resolves to the key itself instead of throwing, so a missing
 * translation degrades the response text rather than turning it into a 500.
 */
@Component
@RequiredArgsConstructor
public class CustomMessageSource {

    private final MessageSource messageSource;

    public String get(String code) {
        return get(code, (Object[]) null);
    }

    public String get(String code, Object... arguments) {
        if (code == null) {
            return null;
        }
        return messageSource.getMessage(code, arguments, code, LocaleThreadStorage.getLocale());
    }
}
