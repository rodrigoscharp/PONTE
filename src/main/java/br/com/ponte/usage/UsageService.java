package br.com.ponte.usage;

import br.com.ponte.consent.ConsentRecordRepository;
import br.com.ponte.consent.ConsentRequiredException;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UsageService {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int TOP_SYMBOLS_LIMIT = 10;

    private final UsageEventRepository events;
    private final ConsentRecordRepository consents;
    private final SymbolRepository symbols;

    public UsageService(UsageEventRepository events, ConsentRecordRepository consents,
                        SymbolRepository symbols) {
        this.events = events;
        this.consents = consents;
        this.symbols = symbols;
    }

    /**
     * LGPD: coleta de eventos de uso só acontece com consentimento ativo
     * do responsável. A regra vive aqui, no domínio — nenhum outro caminho
     * grava UsageEvent.
     */
    @Transactional
    public UsageEvent record(Long childId, Long symbolId, UsageEventType type, Instant occurredAt) {
        if (!consents.existsByChildProfileIdAndRevokedAtIsNull(childId)) {
            throw new ConsentRequiredException(
                    "Este perfil não possui consentimento ativo do responsável; eventos de uso não podem ser registrados (LGPD).");
        }
        if (type == UsageEventType.SYMBOL_TAP && symbolId == null) {
            throw new IllegalArgumentException("Evento SYMBOL_TAP exige symbolId.");
        }
        Instant when = occurredAt != null ? occurredAt : Instant.now();
        return events.save(new UsageEvent(childId, symbolId, type, when));
    }

    public UsageSummaryResponse summary(Long childId, LocalDate date) {
        Instant start = date.atStartOfDay(ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<UsageEvent> dayEvents = events.findByChildProfileIdAndOccurredAtBetween(childId, start, end);

        long sentences = countByType(dayEvents, UsageEventType.SENTENCE_SPOKEN);
        long predictions = countByType(dayEvents, UsageEventType.PREDICTION_ACCEPTED);

        Map<Long, Long> tapsBySymbol = dayEvents.stream()
                .filter(e -> e.getEventType() == UsageEventType.SYMBOL_TAP)
                .collect(Collectors.groupingBy(UsageEvent::getSymbolId, Collectors.counting()));
        long totalTaps = tapsBySymbol.values().stream().mapToLong(Long::longValue).sum();

        Map<Long, Symbol> byId = symbols.findAllById(tapsBySymbol.keySet()).stream()
                .collect(Collectors.toMap(Symbol::getId, Function.identity()));

        List<UsageSummaryResponse.SymbolCount> top = tapsBySymbol.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_SYMBOLS_LIMIT)
                .map(e -> {
                    Symbol s = byId.get(e.getKey());
                    return new UsageSummaryResponse.SymbolCount(
                            e.getKey(),
                            s != null ? s.getLabel() : "símbolo removido",
                            s != null ? s.getCategory().name() : "PERSONALIZADO",
                            e.getValue());
                })
                .toList();

        return new UsageSummaryResponse(date, totalTaps, sentences, predictions, top);
    }

    private long countByType(List<UsageEvent> list, UsageEventType type) {
        return list.stream().filter(e -> e.getEventType() == type).count();
    }
}
