package br.com.ponte.symbol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    /** Prancha da criança: símbolos globais + personalizados dela, na ordem estável da grade. */
    @Query("select s from Symbol s where s.childProfileId is null or s.childProfileId = :childId order by s.gridPosition asc, s.id asc")
    List<Symbol> boardFor(@Param("childId") Long childId);

    @Query("select coalesce(max(s.gridPosition), -1) from Symbol s where s.childProfileId is null or s.childProfileId = :childId")
    int maxGridPosition(@Param("childId") Long childId);
}
