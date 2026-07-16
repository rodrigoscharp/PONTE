package br.com.ponte.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    List<UsageEvent> findByChildProfileIdAndOccurredAtBetween(Long childProfileId, Instant start, Instant end);
}
