package br.com.ponte.consent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/consent")
public class ConsentController {

    public record ConsentRequest(@NotBlank String guardianName, @NotBlank String purpose) {}

    private final ConsentRecordRepository consents;

    public ConsentController(ConsentRecordRepository consents) {
        this.consents = consents;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void grant(@PathVariable Long profileId, @Valid @RequestBody ConsentRequest request) {
        consents.save(new ConsentRecord(profileId, request.guardianName(), request.purpose()));
    }

    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void revoke(@PathVariable Long profileId) {
        consents.findByChildProfileIdAndRevokedAtIsNull(profileId)
                .forEach(ConsentRecord::revoke);
    }
}
