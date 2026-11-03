package com.kfokam48.gestiondestock.inventory.infrastructure.persistence;

import com.kfokam48.gestiondestock.inventory.domain.model.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

  Optional<Stock> findByArticleIdAndSiteId(Long articleId, Long siteId);

  List<Stock> findAllByArticleId(Long articleId);

  List<Stock> findAllBySiteId(Long siteId);

  /**
   * Phase 5g : le perimetre est filtre EN SQL, pas en Java apres un {@code findAll()}. Deux
   * raisons, et la premiere suffit : un filtrage en aval est une protection qu'on peut oublier
   * d'appliquer — c'est exactement ce qui est arrive a
   * {@code getGlobalStockSummary}, qui renvoyait le stock de tous les tenants. La seconde est
   * qu'un rapport ne doit pas charger la table entiere pour en jeter la majeure partie.
   *
   * <p>L'organisation d'un stock se lit par {@code Stock -> Site -> City -> Organization} : la
   * ligne de stock ne porte pas d'organisation propre.
   */
  @Query("select s from Stock s where s.site.city.organization.id = :organizationId")
  List<Stock> findAllByOrganizationId(@Param("organizationId") Long organizationId);

  /**
   * Phase 5g : meme filtre, plus la condition de rupture evaluee en SQL. {@code quantiteReservee}
   * est soustraite ici parce que {@code quantiteDisponible} est calculee et non persistee — la
   * condition doit donc reproduire {@code Stock.getQuantiteDisponible()}.
   */
  @Query("select s from Stock s where s.site.city.organization.id = :organizationId "
      + "and s.seuilAlerte is not null "
      + "and (s.quantitePhysique - s.quantiteReservee) <= s.seuilAlerte")
  List<Stock> findLowStockByOrganizationId(@Param("organizationId") Long organizationId);

}
