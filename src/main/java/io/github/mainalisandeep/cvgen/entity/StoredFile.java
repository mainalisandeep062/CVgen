package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.FileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Bytes we own, at a storage key we control.
 * <p>
 * Named {@code StoredFile} rather than {@code File} so it never collides with
 * {@link java.io.File} at a call site.
 * <p>
 * Owners point here ({@code users.profile_picture_file_id}, later {@code cvs.pdf_file_id});
 * this entity deliberately carries no owner_type/owner_id pair back, since a polymorphic
 * owner cannot be foreign-keyed and would trade integrity for genericity.
 */
@Entity
@Table(name = "files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StoredFile extends BaseEntity {

    /** Key inside the configured storage root. Opaque to callers - only the storage service resolves it. */
    @Column(name = "storage_key", nullable = false, length = 512, unique = true)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 30)
    private FileType fileType;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    /**
     * Provenance for a copy taken from a linked provider: which identity it came from and
     * the URL it was fetched at. Used to label the picture ("synced from Google") and to
     * skip a re-download when the provider URL has not moved. Never read to display.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_identity_id")
    private UserIdentity sourceIdentity;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;
}
