package de.hinundhergestellt.jhuh.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SyncProductRepository extends JpaRepository<SyncProduct, UUID> {
}
