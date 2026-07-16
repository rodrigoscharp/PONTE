package br.com.ponte.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    boolean existsByChildProfileIdAndRevokedAtIsNull(Long childProfileId);

    List<ConsentRecord> findByChildProfileIdAndRevokedAtIsNull(Long childProfileId);
}
