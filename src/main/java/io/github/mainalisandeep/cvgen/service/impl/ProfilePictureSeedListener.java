package io.github.mainalisandeep.cvgen.service.impl;

import io.github.mainalisandeep.cvgen.records.ProfilePictureSeedRequested;
import io.github.mainalisandeep.cvgen.service.ProfilePictureService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gives a brand-new account its first picture.
 * <p>
 * After commit, so the user and identity rows the seed reads are actually there, and async so
 * the provider download never lands on the login request thread.
 */
@Component
@RequiredArgsConstructor
public class ProfilePictureSeedListener {

    private final ProfilePictureService profilePictureService;

    @Async
    @TransactionalEventListener
    public void onSeedRequested(ProfilePictureSeedRequested event) {
        profilePictureService.seedFromIdentityIfUnset(event.userId(), event.identityId());
    }
}
