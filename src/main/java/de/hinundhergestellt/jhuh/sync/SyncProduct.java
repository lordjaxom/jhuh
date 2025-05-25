package de.hinundhergestellt.jhuh.sync;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(indexes = @Index(columnList = "shopifyId"))
public class SyncProduct {

    @Id
    private UUID id;

    @Column
    private @Nullable String shopifyId;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SyncVariant> variants;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> tags;

    public SyncProduct(String shopifyId, List<String> tags) {
        this.id = UUID.randomUUID();
        this.shopifyId = shopifyId;
        variants = new ArrayList<>();
        this.tags = tags;
    }

    protected SyncProduct() {
        // for JPA
    }

    public List<SyncVariant> getVariants() {
        return variants;
    }

    public List<String> getTags() {
        return tags;
    }
}
