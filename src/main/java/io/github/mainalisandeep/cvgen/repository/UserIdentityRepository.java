package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

    @Query("SELECT ui FROM UserIdentity ui JOIN FETCH ui.user WHERE ui.provider = :provider AND ui.providerId = :providerId")
    Optional<UserIdentity> findByProviderAndProviderId(@Param("provider")String provider,
                                                       @Param("providerId")String providerId);

    @Query("SELECT ui FROM UserIdentity ui WHERE ui.user.id = :id")
    List<UserIdentity> findByUserId(@Param("id") UUID id);

    @Query("SELECT ui FROM UserIdentity ui WHERE ui.user.id IN :userIds")
    List<UserIdentity> findByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    /** fk_user_identities_user_id does not cascade, so account deletion removes these first. */
    @Modifying
    @Query("DELETE FROM UserIdentity ui WHERE ui.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
