package io.github.mainalisandeep.cvgen.dto;

/**
 * @param template the template, now {@code unlocked}
 * @param balance  the caller's credit balance after paying, for the nav's credits pill
 */
public record TemplateUnlockResponseDto(CvTemplateResponseDto template, int balance) {
}
