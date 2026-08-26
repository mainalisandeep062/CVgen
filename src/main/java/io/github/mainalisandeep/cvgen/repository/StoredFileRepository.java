package io.github.mainalisandeep.cvgen.repository;

import io.github.mainalisandeep.cvgen.entity.StoredFile;
import io.github.mainalisandeep.cvgen.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByIdAndFileType(UUID id, FileType fileType);

    /**
     * True while any user still has this file selected as their picture. Guards the delete
     * that follows a replacement: {@code fk_users_profile_picture_file} is ON DELETE SET NULL,
     * so a shared row would be silently unhooked from someone else instead of erroring.
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.profilePictureFile.id = :fileId")
    boolean isReferencedAsProfilePicture(@Param("fileId") UUID fileId);
}
