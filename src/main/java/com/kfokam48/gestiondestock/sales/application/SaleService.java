package com.kfokam48.gestiondestock.sales.application;

import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import java.time.Instant;
import java.util.List;

public interface SaleService {

  /**
   * Cree la vente et sort le stock immediatement, ligne par ligne, via InventoryFacade. Si une
   * seule ligne echoue par manque de stock, toute la vente est annulee (rollback transactionnel)
   * : pas de vente partiellement enregistree.
   */
  SaleDto create(SaleDto dto, List<SaleLineDto> lines, Long userId, Long organizationId);

  // Phase 5b-2b : organizationId verifie contre celui du site de la vente (derive via
  // Site -> City -> Organization), meme 404 qu'un id inexistant en cas de mismatch - voir
  // docs/phase-5b2b-report.md.
  SaleDto findById(Long id, Long organizationId);

  SaleDto findByCode(String code, Long organizationId);

  List<SaleDto> findAll(Long organizationId);

  List<SaleLineDto> findLines(Long saleId);

  /**
   * Toutes les lignes de vente referencant cet article, tous sites/ventes confondus. Point
   * d'entree public pour tout module qui a besoin de l'historique des ventes d'un article
   * (ex. catalog) sans acceder directement a SaleLineRepository/SaleLine (internes au module).
   */
  List<SaleLineDto> findLinesByArticleId(Long articleId);

  List<SaleDto> findAllBySiteAndPeriod(Long siteId, Instant from, Instant to);

}
