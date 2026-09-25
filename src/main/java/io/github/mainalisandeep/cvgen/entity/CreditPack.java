package io.github.mainalisandeep.cvgen.entity;

import io.github.mainalisandeep.cvgen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** A purchasable bundle of credits. */
@Entity
@Table(name = "credit_packs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CreditPack extends BaseEntity {

    public static final String DEFAULT_CURRENCY = "NPR";

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "credits", nullable = false)
    private int credits;

    /** Minor units (paisa). Never a floating-point amount. */
    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = DEFAULT_CURRENCY;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "highlighted", nullable = false)
    private boolean highlighted;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
