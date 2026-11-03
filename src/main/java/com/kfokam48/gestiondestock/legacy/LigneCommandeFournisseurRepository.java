package com.kfokam48.gestiondestock.legacy;

import com.kfokam48.gestiondestock.legacy.LigneCommandeFournisseur;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LigneCommandeFournisseurRepository extends JpaRepository<LigneCommandeFournisseur, Long> {

  List<LigneCommandeFournisseur> findAllByCommandeFournisseurId(Long idCommande);

  List<LigneCommandeFournisseur> findAllByArticleId(Long idCommande);
}
