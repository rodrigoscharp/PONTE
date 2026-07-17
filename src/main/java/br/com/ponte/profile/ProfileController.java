package br.com.ponte.profile;

import br.com.ponte.consent.ConsentRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    public record ProfileResponse(Long id, String displayName, boolean hasActiveConsent) {}

    private final ChildProfileRepository profiles;
    private final ConsentRecordRepository consents;

    public ProfileController(ChildProfileRepository profiles, ConsentRecordRepository consents) {
        this.profiles = profiles;
        this.consents = consents;
    }

    @GetMapping
    public List<ProfileResponse> list() {
        return profiles.findAll().stream()
                .map(p -> new ProfileResponse(
                        p.getId(),
                        p.getDisplayName(),
                        consents.existsByChildProfileIdAndRevokedAtIsNull(p.getId())))
                .toList();
    }
}
