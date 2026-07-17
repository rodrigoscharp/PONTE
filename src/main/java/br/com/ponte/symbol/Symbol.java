package br.com.ponte.symbol;

import jakarta.persistence.*;

/**
 * Símbolo da prancha de comunicação.
 *
 * Motor planning (requisito de arquitetura): gridPosition é append-only.
 * É atribuída uma única vez na criação (max + 1), a coluna é
 * updatable = false e não existe setter nem endpoint de reposicionamento.
 * A prancha NUNCA é reorganizada — símbolos novos entram sempre no fim.
 */
@Entity
@Table(name = "board_symbol")
public class Symbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SymbolCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageType imageType;

    @Column(nullable = false)
    private String imageRef;

    @Column(nullable = false, updatable = false)
    private int gridPosition;

    private Long childProfileId; // null = símbolo padrão global

    protected Symbol() {}

    public Symbol(String label, SymbolCategory category, ImageType imageType,
                  String imageRef, int gridPosition, Long childProfileId) {
        this.label = label;
        this.category = category;
        this.imageType = imageType;
        this.imageRef = imageRef;
        this.gridPosition = gridPosition;
        this.childProfileId = childProfileId;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public SymbolCategory getCategory() { return category; }
    public ImageType getImageType() { return imageType; }
    public String getImageRef() { return imageRef; }
    public int getGridPosition() { return gridPosition; }
    public Long getChildProfileId() { return childProfileId; }
}
