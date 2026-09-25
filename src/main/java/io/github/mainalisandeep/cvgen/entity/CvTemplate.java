package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * An admin-managed template: a branded variant of one
 * {@link io.github.mainalisandeep.cvgen.enums.CvTemplateLayout}.
 * <p>
 * The row never carries rendering logic, only choices the layout already supports, which is what
 * keeps every row renderable. The service enforces that; this entity does not.
 */
@Entity
@Table(name = "cv_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CvTemplate extends BaseEntity {

    /** Stored in {@code cvs.template_key}. Immutable after creation. */
    @Column(name = "template_key", nullable = false, length = 64, unique = true, updatable = false)
    private String templateKey;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** {@code CvTemplateLayout} key. */
    @Column(name = "layout", nullable = false, length = 64)
    private String layout;

    @Column(name = "accent_color", length = 7)
    private String accentColor;

    /** Always a subset of the layout's sections. Read and written whole, so a jsonb array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_sections", nullable = false)
    @Builder.Default
    private List<CvSectionType> supportedSections = new ArrayList<>();

    @Column(name = "premium", nullable = false)
    private boolean premium;

    /** Always 0 unless {@link #premium}, mirrored by {@code chk_cv_templates_free_costs_nothing}. */
    @Column(name = "credit_cost", nullable = false)
    private int creditCost;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
