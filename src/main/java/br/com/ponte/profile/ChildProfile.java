package br.com.ponte.profile;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Perfil da criança. LGPD: displayName é um apelido — nunca armazenar
 * nome completo, documento ou dado que identifique a criança diretamente.
 */
@Entity
public class ChildProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ChildProfile() {}

    public ChildProfile(String displayName) {
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
