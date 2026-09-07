package io.github.mainalisandeep.cvgen.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.CvStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One CV: list metadata in columns, the section tree in a single jsonb document.
 * <p>
 * {@code content} is typed {@link JsonNode} rather than a POJO on purpose. A typed
 * deserialiser rejects section types it does not know about, which would let an older
 * server silently destroy a newer client's document on the next save. Shape is checked in
 * the service layer against {@code schemaVersion} instead.
 */
@Entity
@Table(name = "cvs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Cv extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** User-facing name of the CV, not the candidate's name. */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** Key into the template registry; resolved at render time, unvalidated until that exists. */
    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    /** Language the CV itself is written in, independent of the caller's UI locale. */
    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private CvStatus status = CvStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private JsonNode content;

    /**
     * Optimistic lock. Two browser tabs on one CV is the normal case, not the edge case;
     * the column exists now so the editing phase can surface a 409 without a migration.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private long version = 0L;
}
