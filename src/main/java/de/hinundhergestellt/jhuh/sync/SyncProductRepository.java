package de.hinundhergestellt.jhuh.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface SyncProductRepository extends JpaRepository<SyncProduct, UUID> {

    Stream<SyncProduct> findAllBy();

    Optional<SyncProduct> findByShopifyId(String shopifyId);
}
