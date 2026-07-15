package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.dto.UserResponseDto;
import io.github.mainalisandeep.cvgen.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    @Query(
            nativeQuery = true,
            value = """
                    SELECT *
                    FROM users u
                    WHERE u.id = :id
            """
    )
    User findByUserId(UUID id);
}
