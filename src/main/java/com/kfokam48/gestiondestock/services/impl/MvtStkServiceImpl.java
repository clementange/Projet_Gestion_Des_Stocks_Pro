package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.dto.MvtStkDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockMovementDto;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementType;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.model.SourceMvtStk;
import com.kfokam48.gestiondestock.model.TypeMvtStk;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.services.MvtStkService;
import com.kfokam48.gestiondestock.validator.MvtStkValidator;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// Phase 22 : /mvtstk/* garde son contrat HTTP (corps JSON MvtStkDto, pas les query params du
// controleur /stocks/* neuf) mais est desormais re-backe par inventory.Stock/StockMovement
// (Phase 6), sur le Site par defaut de l'organisation (Phase 16). Changement de comportement
// assume : une sortie qui depasserait le stock disponible est desormais refusee (invariant du
// module inventory), au lieu de rendre un stock negatif comme le faisait le legacy.
@Service
@Slf4j
public class MvtStkServiceImpl implements MvtStkService {

  private InventoryFacade inventoryFacade;
  private ArticleService articleService;
  private EntrepriseRepository entrepriseRepository;
  private OrganizationService organizationService;

  @Autowired
  public MvtStkServiceImpl(InventoryFacade inventoryFacade, ArticleService articleService,
      EntrepriseRepository entrepriseRepository, OrganizationService organizationService) {
    this.inventoryFacade = inventoryFacade;
    this.articleService = articleService;
    this.entrepriseRepository = entrepriseRepository;
    this.organizationService = organizationService;
  }

  @Override
  public BigDecimal stockReelArticle(Long idArticle) {
    if (idArticle == null) {
      log.warn("ID article is NULL");
      return BigDecimal.valueOf(-1);
    }
    articleService.findById(idArticle);
    return inventoryFacade.getTotalStockForArticle(idArticle);
  }

  @Override
  public List<MvtStkDto> mvtStkArticle(Long idArticle) {
    SiteDto site = resolveDefaultSite(currentIdEntreprise());
    return inventoryFacade.findMovements(idArticle, site.getId()).stream()
        .map(movement -> toMvtStkDto(movement, null))
        .collect(Collectors.toList());
  }

  @Override
  public MvtStkDto entreeStock(MvtStkDto dto) {
    return applyMovement(dto, TypeMvtStk.ENTREE);
  }

  @Override
  public MvtStkDto sortieStock(MvtStkDto dto) {
    return applyMovement(dto, TypeMvtStk.SORTIE);
  }

  @Override
  public MvtStkDto correctionStockPos(MvtStkDto dto) {
    return applyMovement(dto, TypeMvtStk.CORRECTION_POS);
  }

  @Override
  public MvtStkDto correctionStockNeg(MvtStkDto dto) {
    return applyMovement(dto, TypeMvtStk.CORRECTION_NEG);
  }

  private MvtStkDto applyMovement(MvtStkDto dto, TypeMvtStk requestedType) {
    List<String> errors = MvtStkValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Article is not valid {}", dto);
      throw new InvalidEntityException("Le mouvement du stock n'est pas valide", ErrorCodes.MVT_STK_NOT_VALID, errors);
    }

    Long idEntreprise = dto.getIdEntreprise() != null ? dto.getIdEntreprise() : currentIdEntreprise();
    SiteDto site = resolveDefaultSite(idEntreprise);
    Long articleId = dto.getArticle().getId();
    StockMovementSource source = toStockMovementSource(dto.getSourceMvt());
    Long userId = currentUserId();
    BigDecimal absQuantity = dto.getQuantite().abs();

    StockMovementDto movement;
    switch (requestedType) {
      case ENTREE:
        movement = inventoryFacade.receive(articleId, site.getId(), absQuantity, source, null, userId);
        break;
      case SORTIE:
        movement = inventoryFacade.issue(articleId, site.getId(), absQuantity, source, null, userId);
        break;
      case CORRECTION_POS:
        movement = inventoryFacade.correct(articleId, site.getId(), absQuantity, null, userId);
        break;
      case CORRECTION_NEG:
      default:
        movement = inventoryFacade.correct(articleId, site.getId(), absQuantity.negate(), null, userId);
        break;
    }

    return toMvtStkDto(movement, idEntreprise);
  }

  private StockMovementSource toStockMovementSource(SourceMvtStk source) {
    if (source == null) {
      return StockMovementSource.CORRECTION;
    }
    switch (source) {
      case COMMANDE_CLIENT:
        return StockMovementSource.COMMANDE_CLIENT;
      case COMMANDE_FOURNISSEUR:
        return StockMovementSource.COMMANDE_FOURNISSEUR;
      case VENTE:
        return StockMovementSource.VENTE;
      default:
        return StockMovementSource.CORRECTION;
    }
  }

  private TypeMvtStk toTypeMvtStk(StockMovementType type) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case ENTREE:
        return TypeMvtStk.ENTREE;
      case SORTIE:
        return TypeMvtStk.SORTIE;
      case CORRECTION_POSITIVE:
        return TypeMvtStk.CORRECTION_POS;
      case CORRECTION_NEGATIVE:
        return TypeMvtStk.CORRECTION_NEG;
      default:
        return null;
    }
  }

  private SourceMvtStk toSourceMvtStk(StockMovementSource source) {
    if (source == null) {
      return null;
    }
    switch (source) {
      case COMMANDE_CLIENT:
        return SourceMvtStk.COMMANDE_CLIENT;
      case COMMANDE_FOURNISSEUR:
        return SourceMvtStk.COMMANDE_FOURNISSEUR;
      case VENTE:
        return SourceMvtStk.VENTE;
      default:
        return null;
    }
  }

  private MvtStkDto toMvtStkDto(StockMovementDto movement, Long fallbackIdEntreprise) {
    if (movement == null) {
      return null;
    }
    Long idEntreprise = fallbackIdEntreprise != null ? fallbackIdEntreprise : resolveIdEntrepriseFromSite(movement.getSite());
    return MvtStkDto.builder()
        .id(movement.getId())
        .dateMvt(movement.getDateMvt())
        .quantite(movement.getQuantite())
        .article(movement.getArticle())
        .typeMvt(toTypeMvtStk(movement.getType()))
        .sourceMvt(toSourceMvtStk(movement.getSource()))
        .idEntreprise(idEntreprise)
        .build();
  }

  private Long resolveIdEntrepriseFromSite(SiteDto site) {
    if (site == null || site.getCity() == null || site.getCity().getOrganization() == null) {
      return null;
    }
    Long organizationId = site.getCity().getOrganization().getId();
    return entrepriseRepository.findByOrganizationId(organizationId).map(Entreprise::getId).orElse(null);
  }

  private SiteDto resolveDefaultSite(Long idEntreprise) {
    if (idEntreprise == null) {
      throw new InvalidEntityException("Le mouvement du stock n'est pas valide", ErrorCodes.MVT_STK_NOT_VALID,
          List.of("Impossible de determiner l'entreprise de ce mouvement (aucune idEntreprise fournie ni resolue depuis l'utilisateur connecte)"));
    }
    Entreprise entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow(
        () -> new InvalidEntityException("Le mouvement du stock n'est pas valide", ErrorCodes.MVT_STK_NOT_VALID,
            List.of("Aucune entreprise avec l'ID = " + idEntreprise + " n'a ete trouvee dans la BDD")));
    return organizationService.ensureDefaultSite(entreprise.getOrganizationId());
  }

  private Long currentUserId() {
    ExtendedUser principal = currentPrincipal();
    return principal != null ? principal.getIdUtilisateur() : null;
  }

  private Long currentIdEntreprise() {
    ExtendedUser principal = currentPrincipal();
    return principal != null ? principal.getIdEntreprise() : null;
  }

  private ExtendedUser currentPrincipal() {
    Object principal = SecurityContextHolder.getContext().getAuthentication() != null
        ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
        : null;
    return principal instanceof ExtendedUser extendedUser ? extendedUser : null;
  }
}
