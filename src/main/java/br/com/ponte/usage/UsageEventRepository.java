package br.com.ponte.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    /** Intervalo semiaberto [start, end): evento exatamente à meia-noite conta num único dia. */
    @Query("select e from UsageEvent e where e.childProfileId = :childId and e.occurredAt >= :start and e.occurredAt < :end")
    List<UsageEvent> findByChildProfileIdAndOccurredAtBetween(@Param("childId") Long childProfileId,
                                                              @Param("start") Instant start,
                                                              @Param("end") Instant end);
}
