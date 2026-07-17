package br.com.ponte.consent;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Registro de consentimento do responsável (LGPD, art. 14 — dados de
 * crianças exigem consentimento específico de ao menos um dos pais ou
 * responsável). Consentimento ativo = revokedAt == null.
 */
@Entity
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long childProfileId;

    @Column(nullable = false)
    private String guardianName;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private Instant grantedAt = Instant.now();

    private Instant revokedAt;

    protected ConsentRecord() {}

    public ConsentRecord(Long childProfileId, String guardianName, String purpose) {
        this.childProfileId = childProfileId;
        this.guardianName = guardianName;
        this.purpose = purpose;
    }

    public Long getId() { return id; }
    public Long getChildProfileId() { return childProfileId; }
    public String getGuardianName() { return guardianName; }
    public String getPurpose() { return purpose; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isActive() { return revokedAt == null; }

    public void revoke() { this.revokedAt = Instant.now(); }
}
