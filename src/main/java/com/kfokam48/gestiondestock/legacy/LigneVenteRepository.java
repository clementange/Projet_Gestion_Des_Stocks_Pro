package com.kfokam48.gestiondestock.legacy;

import com.kfokam48.gestiondestock.legacy.LigneVente;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LigneVenteRepository extends JpaRepository<LigneVente, Long> {

  List<LigneVente> findAllByArticleId(Long idArticle);

  List<LigneVente> findAllByVenteId(Long id);
}
