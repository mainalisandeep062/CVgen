package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    Optional<UserIdentity> findByProviderAndProviderId(String provider, String providerId);

    @Query("SELECT ui FROM UserIdentity ui WHERE ui.user.id = :id")
    List<UserIdentity> findByUserId(UUID id);
}
