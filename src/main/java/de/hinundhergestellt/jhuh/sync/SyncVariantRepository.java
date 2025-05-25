package de.hinundhergestellt.jhuh.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SyncVariantRepository extends JpaRepository<SyncVariant, UUID> {

    boolean existsByBarcode(String barcode);

    Optional<SyncVariant> findByBarcode(String barcode);
}
