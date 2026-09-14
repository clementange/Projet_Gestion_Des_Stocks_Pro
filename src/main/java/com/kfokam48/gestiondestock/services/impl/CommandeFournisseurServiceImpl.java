package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.dto.CommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.FournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.model.EtatCommande;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.purchasing.application.PurchaseOrderService;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderStatus;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.services.CommandeFournisseurService;
import com.kfokam48.gestiondestock.validator.CommandeFournisseurValidator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 21 : /commandesfournisseurs/* garde son contrat HTTP (CommandeFournisseurDto) mais est
// desormais re-backe par purchasing.PurchaseOrder (Phase 7), sur le Supplier (Phase 18) et le
// Site par defaut de l'organisation (Phase 16). Effet de bord attendu et documente : le bug de
// double comptage de stock du legacy (entree a save() ET a updateEtatCommande(LIVREE))
// disparait, seul PurchaseOrderService.receiveLine touche desormais le stock.
@Service
@Slf4j
public class CommandeFournisseurServiceImpl implements CommandeFournisseurService {

  private PurchaseOrderService purchaseOrderService;
  private SupplierService supplierService;
  private ArticleService articleService;
  private EntrepriseRepository entrepriseRepository;
  private OrganizationService organizationService;

  @Autowired
  public CommandeFournisseurServiceImpl(PurchaseOrderService purchaseOrderService, SupplierService supplierService,
      ArticleService articleService, EntrepriseRepository entrepriseRepository, OrganizationService organizationService) {
    this.purchaseOrderService = purchaseOrderService;
    this.supplierService = supplierService;
    this.articleService = articleService;
    this.entrepriseRepository = entrepriseRepository;
    this.organizationService = organizationService;
  }

  @Override
  public CommandeFournisseurDto save(CommandeFournisseurDto dto) {
    List<String> errors = CommandeFournisseurValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Commande fournisseur n'est pas valide");
      throw new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID, errors);
    }

    try {
      supplierService.findById(dto.getFournisseur().getId());
    } catch (EntityNotFoundException notFound) {
      log.warn("Fournisseur with ID {} was not found in the DB", dto.getFournisseur().getId());
      throw new EntityNotFoundException("Aucun fournisseur avec l'ID" + dto.getFournisseur().getId() + " n'a ete trouve dans la BDD",
          ErrorCodes.FOURNISSEUR_NOT_FOUND);
    }

    if (dto.getLigneCommandeFournisseurs() != null) {
      dto.getLigneCommandeFournisseurs().forEach(ligne -> articleService.findById(ligne.getArticle().getId()));
    }

    Long idEntreprise = dto.getIdEntreprise() != null ? dto.getIdEntreprise() : currentIdEntreprise();
    SiteDto site = resolveDefaultSite(idEntreprise);

    PurchaseOrderDto orderDto = PurchaseOrderDto.builder()
        .code(dto.getCode())
        .supplierId(dto.getFournisseur().getId())
        .site(site)
        .status(toPurchaseOrderStatus(dto.getEtatCommande()))
        .build();

    List<PurchaseOrderLineDto> lines = dto.getLigneCommandeFournisseurs() == null ? Collections.emptyList()
        : dto.getLigneCommandeFournisseurs().stream()
            .map(ligne -> PurchaseOrderLineDto.builder()
                .article(ligne.getArticle())
                .quantiteCommandee(ligne.getQuantite())
                .prixUnitaire(ligne.getPrixUnitaire())
                .build())
            .collect(Collectors.toList());

    PurchaseOrderDto saved = purchaseOrderService.create(orderDto, lines);
    return toCommandeFournisseurDto(saved, idEntreprise);
  }

  @Override
  public CommandeFournisseurDto findById(Long id) {
    if (id == null) {
      log.error("Commande fournisseur ID is NULL");
      return null;
    }
    return toCommandeFournisseurDto(purchaseOrderService.findById(id), null);
  }

  @Override
  public CommandeFournisseurDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("Commande fournisseur CODE is NULL");
      return null;
    }
    return toCommandeFournisseurDto(purchaseOrderService.findByCode(code), null);
  }

  @Override
  public List<CommandeFournisseurDto> findAll() {
    return purchaseOrderService.findAll().stream()
        .map(order -> toCommandeFournisseurDto(order, null))
        .collect(Collectors.toList());
  }

  @Override
  public List<LigneCommandeFournisseurDto> findAllLignesCommandesFournisseurByCommandeFournisseurId(Long idCommande) {
    return purchaseOrderService.findLines(idCommande).stream()
        .map(line -> LigneCommandeFournisseurDto.builder()
            .id(line.getId())
            .article(line.getArticle())
            .quantite(line.getQuantiteCommandee())
            .prixUnitaire(line.getPrixUnitaire())
            .build())
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    throw new InvalidOperationException(
        "La suppression d'une commande fournisseur n'est plus supportee : utilisez l'annulation (annuler)",
        ErrorCodes.COMMANDE_FOURNISSEUR_ALREADY_IN_USE);
  }

  @Override
  public CommandeFournisseurDto updateEtatCommande(Long idCommande, EtatCommande etatCommande) {
    PurchaseOrderDto current = purchaseOrderService.findById(idCommande);
    if (current.getStatus() == PurchaseOrderStatus.RECUE) {
      throw new InvalidOperationException("Impossible de modifier la commande lorsqu'elle est livree",
          ErrorCodes.COMMANDE_FOURNISSEUR_NON_MODIFIABLE);
    }

    if (etatCommande == EtatCommande.VALIDEE && current.getStatus() == PurchaseOrderStatus.BROUILLON) {
      purchaseOrderService.validate(idCommande);
    } else if (etatCommande == EtatCommande.LIVREE && current.getStatus() == PurchaseOrderStatus.VALIDEE) {
      receiveAllLinesInFull(idCommande);
    } else {
      throw new InvalidOperationException(
          "Transition non supportee pour la commande #" + idCommande + " : " + current.getStatus() + " -> " + etatCommande,
          ErrorCodes.COMMANDE_FOURNISSEUR_TRANSITION_UNSUPPORTED);
    }

    return toCommandeFournisseurDto(purchaseOrderService.findById(idCommande), null);
  }

  /**
   * "LIVREE" en legacy = reception complete de toutes les lignes en une fois (l'unique point
   * d'entree de stock, PurchaseOrderService.receiveLine, evite le double comptage du legacy).
   */
  private void receiveAllLinesInFull(Long idCommande) {
    Long userId = currentUserId();
    List<PurchaseOrderLineDto> lines = purchaseOrderService.findLines(idCommande);
    for (PurchaseOrderLineDto line : lines) {
      BigDecimal remaining = line.getQuantiteRestanteARecevoir();
      if (remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
        purchaseOrderService.receiveLine(idCommande, line.getId(), remaining, userId);
      }
    }
  }

  @Override
  public CommandeFournisseurDto updateQuantiteCommande(Long idCommande, Long idLigneCommande, BigDecimal quantite) {
    throw mutationUnsupported();
  }

  @Override
  public CommandeFournisseurDto updateFournisseur(Long idCommande, Long idFournisseur) {
    throw mutationUnsupported();
  }

  @Override
  public CommandeFournisseurDto updateArticle(Long idCommande, Long idLigneCommande, Long idArticle) {
    throw mutationUnsupported();
  }

  @Override
  public CommandeFournisseurDto deleteArticle(Long idCommande, Long idLigneCommande) {
    throw mutationUnsupported();
  }

  private InvalidOperationException mutationUnsupported() {
    return new InvalidOperationException(
        "Cette operation n'est plus supportee : une commande fournisseur re-backee par le module purchasing "
            + "ne peut plus etre modifiee ligne par ligne apres creation, seules les transitions d'etat le sont",
        ErrorCodes.COMMANDE_FOURNISSEUR_MUTATION_UNSUPPORTED);
  }

  private PurchaseOrderStatus toPurchaseOrderStatus(EtatCommande etatCommande) {
    if (etatCommande == null) {
      return PurchaseOrderStatus.BROUILLON;
    }
    switch (etatCommande) {
      case VALIDEE:
        return PurchaseOrderStatus.VALIDEE;
      case LIVREE:
        return PurchaseOrderStatus.RECUE;
      case EN_PREPARATION:
      default:
        return PurchaseOrderStatus.BROUILLON;
    }
  }

  private EtatCommande toEtatCommande(PurchaseOrderStatus status) {
    if (status == null) {
      return null;
    }
    switch (status) {
      case VALIDEE:
        return EtatCommande.VALIDEE;
      case RECUE:
        return EtatCommande.LIVREE;
      default:
        return EtatCommande.EN_PREPARATION;
    }
  }

  private SiteDto resolveDefaultSite(Long idEntreprise) {
    if (idEntreprise == null) {
      throw new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,
          List.of("Impossible de determiner l'entreprise de cette commande (aucune idEntreprise fournie ni resolue depuis l'utilisateur connecte)"));
    }
    Entreprise entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow(
        () -> new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.COMMANDE_FOURNISSEUR_NOT_VALID,
            List.of("Aucune entreprise avec l'ID = " + idEntreprise + " n'a ete trouvee dans la BDD")));
    return organizationService.ensureDefaultSite(entreprise.getOrganizationId());
  }

  private CommandeFournisseurDto toCommandeFournisseurDto(PurchaseOrderDto order, Long fallbackIdEntreprise) {
    if (order == null) {
      return null;
    }
    Long idEntreprise = fallbackIdEntreprise != null ? fallbackIdEntreprise : resolveIdEntrepriseFromSite(order);
    return CommandeFournisseurDto.builder()
        .id(order.getId())
        .code(order.getCode())
        .dateCommande(order.getOrderDate())
        .etatCommande(toEtatCommande(order.getStatus()))
        .fournisseur(toFournisseurDto(order.getSupplierId()))
        .idEntreprise(idEntreprise)
        .build();
  }

  private FournisseurDto toFournisseurDto(Long supplierId) {
    if (supplierId == null) {
      return null;
    }
    try {
      SupplierDto supplier = supplierService.findById(supplierId);
      return FournisseurDto.builder()
          .id(supplier.getId())
          .nom(supplier.getNom())
          .prenom(supplier.getPrenom())
          .adresse(supplier.getAdresse())
          .photo(supplier.getPhoto())
          .mail(supplier.getMail())
          .numTel(supplier.getNumTel())
          .build();
    } catch (EntityNotFoundException notFound) {
      return null;
    }
  }

  private Long resolveIdEntrepriseFromSite(PurchaseOrderDto order) {
    if (order.getSite() == null || order.getSite().getCity() == null || order.getSite().getCity().getOrganization() == null) {
      return null;
    }
    Long organizationId = order.getSite().getCity().getOrganization().getId();
    return entrepriseRepository.findByOrganizationId(organizationId).map(Entreprise::getId).orElse(null);
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
