package br.com.ponte.usage;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Evento de uso da prancha. LGPD (minimização): guarda apenas IDs e
 * timestamp — nunca texto livre nem conteúdo da frase.
 */
@Entity
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long childProfileId;

    /** Null para eventos de frase (SENTENCE_SPOKEN, PREDICTION_ACCEPTED). */
    private Long symbolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsageEventType eventType;

    /** Momento do evento — vem do cliente quando o evento ficou na fila offline. */
    @Column(nullable = false)
    private Instant occurredAt;

    protected UsageEvent() {}

    public UsageEvent(Long childProfileId, Long symbolId, UsageEventType eventType, Instant occurredAt) {
        this.childProfileId = childProfileId;
        this.symbolId = symbolId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getChildProfileId() { return childProfileId; }
    public Long getSymbolId() { return symbolId; }
    public UsageEventType getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
}
