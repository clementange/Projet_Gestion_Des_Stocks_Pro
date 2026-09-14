package com.kfokam48.gestiondestock.repository;

import com.kfokam48.gestiondestock.model.LigneCommandeClient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LigneCommandeClientRepository extends JpaRepository<LigneCommandeClient, Long> {


  List<LigneCommandeClient> findAllByCommandeClientId(Long id);

  List<LigneCommandeClient> findAllByArticleId(Long id);
}
