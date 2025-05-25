package de.hinundhergestellt.jhuh.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "idx_syncvariant_barcode", columnList = "barcode"))
public class SyncVariant {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    private SyncProduct product;

    @Column(nullable = false, unique = true)
    private String barcode;

    @Column(nullable = false)
    private boolean deleted;

    public SyncVariant(SyncProduct product, String barcode) {
        id = UUID.randomUUID();
        this.product = product;
        this.barcode = barcode;
        product.getVariants().add(this);
    }

    protected SyncVariant() {
    }

    public SyncProduct getProduct() {
        return product;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
