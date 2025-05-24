package de.hinundhergestellt.jhuh.sync;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class SyncProduct {

    @Id
    private UUID id;

    @Column(unique = true)
    private String barcode;

    @Column
    private int ready2orderId;

    @Column
    private @Nullable String shopifyId;

    public SyncProduct(String barcode) {
        this.id = UUID.randomUUID();
        this.barcode = barcode;
    }

    protected SyncProduct() {
        // for JPA
    }
}
