package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.dto.VentesDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import com.kfokam48.gestiondestock.services.VentesService;
import com.kfokam48.gestiondestock.validator.VentesValidator;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 19 : /ventes/* garde son contrat HTTP (VentesDto) mais est desormais re-backe par
// sales.Sale (Phase 8), sur le Site par defaut de l'organisation miroir (Phase 16) — legacy n'a
// aucune notion de site. DELETE n'a pas d'equivalent (stock deja sorti) : rejete explicitement.
@Service
@Slf4j
public class VentesServiceImpl implements VentesService {

  private SaleService saleService;
  private EntrepriseRepository entrepriseRepository;
  private OrganizationService organizationService;

  @Autowired
  public VentesServiceImpl(SaleService saleService, EntrepriseRepository entrepriseRepository,
      OrganizationService organizationService) {
    this.saleService = saleService;
    this.entrepriseRepository = entrepriseRepository;
    this.organizationService = organizationService;
  }

  @Override
  public VentesDto save(VentesDto dto) {
    List<String> errors = VentesValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Ventes n'est pas valide");
      throw new InvalidEntityException("L'objet vente n'est pas valide", ErrorCodes.VENTE_NOT_VALID, errors);
    }

    Long idEntreprise = dto.getIdEntreprise() != null ? dto.getIdEntreprise() : currentIdEntreprise();
    SiteDto site = resolveDefaultSite(idEntreprise);

    SaleDto saleDto = SaleDto.builder()
        .code(dto.getCode())
        .site(site)
        .comment(dto.getCommentaire())
        .build();

    List<SaleLineDto> lines = dto.getLigneVentes() == null ? Collections.emptyList() : dto.getLigneVentes().stream()
        .map(ligne -> SaleLineDto.builder()
            .article(ligne.getArticle())
            .quantite(ligne.getQuantite())
            .prixUnitaire(ligne.getPrixUnitaire())
            .build())
        .collect(Collectors.toList());

    SaleDto saved = saleService.create(saleDto, lines, currentUserId());

    return toVentesDto(saved, idEntreprise);
  }

  @Override
  public VentesDto findById(Long id) {
    if (id == null) {
      log.error("Ventes ID is NULL");
      return null;
    }
    return toVentesDto(saleService.findById(id), null);
  }

  @Override
  public VentesDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("Vente CODE is NULL");
      return null;
    }
    return toVentesDto(saleService.findByCode(code), null);
  }

  @Override
  public List<VentesDto> findAll() {
    return saleService.findAll().stream()
        .map(sale -> toVentesDto(sale, null))
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    throw new InvalidOperationException(
        "La suppression d'une vente n'est plus supportee : le stock associe a deja ete sorti",
        ErrorCodes.VENTE_DELETE_NOT_SUPPORTED);
  }

  private SiteDto resolveDefaultSite(Long idEntreprise) {
    if (idEntreprise == null) {
      throw new InvalidEntityException("L'objet vente n'est pas valide", ErrorCodes.VENTE_NOT_VALID,
          List.of("Impossible de determiner l'entreprise de cette vente (aucune idEntreprise fournie ni resolue depuis l'utilisateur connecte)"));
    }
    Entreprise entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow(
        () -> new InvalidEntityException("L'objet vente n'est pas valide", ErrorCodes.VENTE_NOT_VALID,
            List.of("Aucune entreprise avec l'ID = " + idEntreprise + " n'a ete trouvee dans la BDD")));
    return organizationService.ensureDefaultSite(entreprise.getOrganizationId());
  }

  private VentesDto toVentesDto(SaleDto sale, Long fallbackIdEntreprise) {
    if (sale == null) {
      return null;
    }
    Long idEntreprise = fallbackIdEntreprise != null ? fallbackIdEntreprise : resolveIdEntrepriseFromSite(sale);
    return VentesDto.builder()
        .id(sale.getId())
        .code(sale.getCode())
        .dateVente(sale.getSaleDate())
        .commentaire(sale.getComment())
        .idEntreprise(idEntreprise)
        .build();
  }

  private Long resolveIdEntrepriseFromSite(SaleDto sale) {
    if (sale.getSite() == null || sale.getSite().getCity() == null || sale.getSite().getCity().getOrganization() == null) {
      return null;
    }
    Long organizationId = sale.getSite().getCity().getOrganization().getId();
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
