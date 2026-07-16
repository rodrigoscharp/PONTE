package br.com.ponte.usage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class UsageController {

    public record UsageEventRequest(
            @NotNull Long childId,
            Long symbolId,
            @NotNull UsageEventType eventType,
            Instant occurredAt) {}

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @PostMapping("/usage-events")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> record(@Valid @RequestBody UsageEventRequest request) {
        UsageEvent saved = usageService.record(
                request.childId(), request.symbolId(), request.eventType(), request.occurredAt());
        return Map.of("id", saved.getId());
    }

    @GetMapping("/usage/summary")
    public UsageSummaryResponse summary(
            @RequestParam Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return usageService.summary(childId, date != null ? date : LocalDate.now(UsageService.ZONE));
    }
}
