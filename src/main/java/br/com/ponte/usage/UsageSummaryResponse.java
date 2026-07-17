package br.com.ponte.usage;

import java.time.LocalDate;
import java.util.List;

public record UsageSummaryResponse(
        LocalDate date,
        long totalTaps,
        long sentencesSpoken,
        long predictionsAccepted,
        List<SymbolCount> topSymbols) {

    public record SymbolCount(Long symbolId, String label, String category, long count) {}
}
