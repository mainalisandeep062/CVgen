package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.mainalisandeep.cvgen.enums.NotificationAudience;
import io.github.mainalisandeep.cvgen.enums.NotificationLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationCreateRequestDto {

    @NotBlank(message = "{validation.notification.title.required}")
    @Size(max = 160, message = "{validation.notification.title.size}")
    private String title;

    @NotBlank(message = "{validation.notification.body.required}")
    @Size(max = 2000, message = "{validation.notification.body.size}")
    private String body;

    @NotNull(message = "{validation.notification.level.required}")
    private NotificationLevel level;

    /**
     * An in-app path or an https URL. The frontend navigates to it, so it is an open-redirect surface:
     * a path may not start with {@code //} or {@code /\}, which browsers treat as another origin, and
     * no whitespace is allowed anywhere.
     */
    @Size(max = 512, message = "{validation.notification.link.size}")
    @Pattern(regexp = "^(/(?![/\\\\])|https://)\\S*$", message = "{validation.notification.link.pattern}")
    private String link;

    @NotNull(message = "{validation.notification.audience.required}")
    private NotificationAudience audience;

    /** Required when {@link #audience} is USER, ignored for a broadcast. */
    @Size(max = 255, message = "{validation.notification.recipient.size}")
    private String recipientEmail;

    @JsonIgnore
    @AssertTrue(message = "{validation.notification.recipient.required}")
    public boolean isRecipientPresent() {
        return audience != NotificationAudience.USER || (recipientEmail != null && !recipientEmail.isBlank());
    }
}
