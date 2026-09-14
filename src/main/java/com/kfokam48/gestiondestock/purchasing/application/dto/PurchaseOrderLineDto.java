package com.kfokam48.gestiondestock.purchasing.application.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderLine;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseOrderLineDto {

  private Long id;

  private Long purchaseOrderId;

  private ArticleDto article;

  private BigDecimal quantiteCommandee;

  private BigDecimal quantiteRecue;

  private BigDecimal quantiteRestanteARecevoir;

  private BigDecimal prixUnitaire;

  public static PurchaseOrderLineDto fromEntity(PurchaseOrderLine line) {
    if (line == null) {
      return null;
    }

    return PurchaseOrderLineDto.builder()
        .id(line.getId())
        .purchaseOrderId(line.getPurchaseOrder() != null ? line.getPurchaseOrder().getId() : null)
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantiteCommandee(line.getQuantiteCommandee())
        .quantiteRecue(line.getQuantiteRecue())
        .quantiteRestanteARecevoir(line.getQuantiteRestanteARecevoir())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  /**
   * Ne fixe ni l'ID, ni la commande parente, ni la quantite recue : une ligne se cree toujours
   * vide de reception, rattachee a sa commande par le service appelant.
   */
  public static PurchaseOrderLine toNewEntity(PurchaseOrderLineDto dto) {
    if (dto == null) {
      return null;
    }

    PurchaseOrderLine line = new PurchaseOrderLine();
    line.setArticle(ArticleDto.toEntity(dto.getArticle()));
    line.setQuantiteCommandee(dto.getQuantiteCommandee());
    line.setPrixUnitaire(dto.getPrixUnitaire());
    line.setQuantiteRecue(BigDecimal.ZERO);

    return line;
  }
}
