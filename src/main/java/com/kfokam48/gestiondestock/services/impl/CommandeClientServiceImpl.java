package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.dto.ClientDto;
import com.kfokam48.gestiondestock.dto.CommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.EtatCommande;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.sales.application.CustomerOrderService;
import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderStatus;
import com.kfokam48.gestiondestock.services.CommandeClientService;
import com.kfokam48.gestiondestock.validator.CommandeClientValidator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 20 : /commandesclients/* garde son contrat HTTP (CommandeClientDto) mais est desormais
// re-backe par sales.CustomerOrder (Phase 8), sur le Customer (Phase 18) et le Site par defaut
// de l'organisation (Phase 16). La machine a etats gardee de CustomerOrder remplace la mutation
// libre du legacy : les transitions d'etat qui correspondent a une transition legale
// (BROUILLON->VALIDEE->...->LIVREE) sont mappees, le reste est rejete explicitement (decision
// actee, voir plan Phase 16-23).
@Service
@Slf4j
public class CommandeClientServiceImpl implements CommandeClientService {

  private CustomerOrderService customerOrderService;
  private CustomerService customerService;
  private ArticleService articleService;
  private EntrepriseRepository entrepriseRepository;
  private OrganizationService organizationService;

  @Autowired
  public CommandeClientServiceImpl(CustomerOrderService customerOrderService, CustomerService customerService,
      ArticleService articleService, EntrepriseRepository entrepriseRepository, OrganizationService organizationService) {
    this.customerOrderService = customerOrderService;
    this.customerService = customerService;
    this.articleService = articleService;
    this.entrepriseRepository = entrepriseRepository;
    this.organizationService = organizationService;
  }

  @Override
  public CommandeClientDto save(CommandeClientDto dto) {
    List<String> errors = CommandeClientValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Commande client n'est pas valide");
      throw new InvalidEntityException("La commande client n'est pas valide", ErrorCodes.COMMANDE_CLIENT_NOT_VALID, errors);
    }

    try {
      customerService.findById(dto.getClient().getId());
    } catch (EntityNotFoundException notFound) {
      log.warn("Client with ID {} was not found in the DB", dto.getClient().getId());
      throw new EntityNotFoundException("Aucun client avec l'ID" + dto.getClient().getId() + " n'a ete trouve dans la BDD",
          ErrorCodes.CLIENT_NOT_FOUND);
    }

    if (dto.getLigneCommandeClients() != null) {
      dto.getLigneCommandeClients().forEach(ligne -> articleService.findById(ligne.getArticle().getId()));
    }

    Long idEntreprise = dto.getIdEntreprise() != null ? dto.getIdEntreprise() : currentIdEntreprise();
    SiteDto site = resolveDefaultSite(idEntreprise);

    CustomerOrderDto orderDto = CustomerOrderDto.builder()
        .code(dto.getCode())
        .customerId(dto.getClient().getId())
        .site(site)
        .status(toCustomerOrderStatus(dto.getEtatCommande()))
        .build();

    List<CustomerOrderLineDto> lines = dto.getLigneCommandeClients() == null ? Collections.emptyList()
        : dto.getLigneCommandeClients().stream()
            .map(ligne -> CustomerOrderLineDto.builder()
                .article(ligne.getArticle())
                .quantite(ligne.getQuantite())
                .prixUnitaire(ligne.getPrixUnitaire())
                .build())
            .collect(Collectors.toList());

    CustomerOrderDto saved = customerOrderService.create(orderDto, lines);
    return toCommandeClientDto(saved, idEntreprise);
  }

  @Override
  public CommandeClientDto findById(Long id) {
    if (id == null) {
      log.error("Commande client ID is NULL");
      return null;
    }
    return toCommandeClientDto(customerOrderService.findById(id), null);
  }

  @Override
  public CommandeClientDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("Commande client CODE is NULL");
      return null;
    }
    return toCommandeClientDto(customerOrderService.findByCode(code), null);
  }

  @Override
  public List<CommandeClientDto> findAll() {
    return customerOrderService.findAll().stream()
        .map(order -> toCommandeClientDto(order, null))
        .collect(Collectors.toList());
  }

  @Override
  public List<LigneCommandeClientDto> findAllLignesCommandesClientByCommandeClientId(Long idCommande) {
    return customerOrderService.findLines(idCommande).stream()
        .map(line -> LigneCommandeClientDto.builder()
            .id(line.getId())
            .article(line.getArticle())
            .quantite(line.getQuantite())
            .prixUnitaire(line.getPrixUnitaire())
            .build())
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    throw new InvalidOperationException(
        "La suppression d'une commande client n'est plus supportee : utilisez l'annulation (annuler)",
        ErrorCodes.COMMANDE_CLIENT_ALREADY_IN_USE);
  }

  @Override
  public CommandeClientDto updateEtatCommande(Long idCommande, EtatCommande etatCommande) {
    CustomerOrderDto current = customerOrderService.findById(idCommande);
    if (current.getStatus() == CustomerOrderStatus.LIVREE) {
      throw new InvalidOperationException("Impossible de modifier la commande lorsqu'elle est livree",
          ErrorCodes.COMMANDE_CLIENT_NON_MODIFIABLE);
    }

    CustomerOrderDto updated;
    if (etatCommande == EtatCommande.VALIDEE && current.getStatus() == CustomerOrderStatus.BROUILLON) {
      updated = customerOrderService.validate(idCommande);
    } else if (etatCommande == EtatCommande.LIVREE && current.getStatus() == CustomerOrderStatus.EXPEDIEE) {
      updated = customerOrderService.deliver(idCommande, currentUserId());
    } else {
      throw new InvalidOperationException(
          "Transition non supportee pour la commande #" + idCommande + " : " + current.getStatus() + " -> " + etatCommande,
          ErrorCodes.COMMANDE_CLIENT_TRANSITION_UNSUPPORTED);
    }

    return toCommandeClientDto(updated, null);
  }

  @Override
  public CommandeClientDto updateQuantiteCommande(Long idCommande, Long idLigneCommande, BigDecimal quantite) {
    throw mutationUnsupported();
  }

  @Override
  public CommandeClientDto updateClient(Long idCommande, Long idClient) {
    throw mutationUnsupported();
  }

  @Override
  public CommandeClientDto updateArticle(Long idCommande, Long idLigneCommande, Long newIdArticle) {
    throw mutationUnsupported();
  }

  @Override
  public CommandeClientDto deleteArticle(Long idCommande, Long idLigneCommande) {
    throw mutationUnsupported();
  }

  private InvalidOperationException mutationUnsupported() {
    return new InvalidOperationException(
        "Cette operation n'est plus supportee : une commande client re-backee par le module sales "
            + "ne peut plus etre modifiee ligne par ligne apres creation, seules les transitions d'etat le sont",
        ErrorCodes.COMMANDE_CLIENT_MUTATION_UNSUPPORTED);
  }

  private CustomerOrderStatus toCustomerOrderStatus(EtatCommande etatCommande) {
    if (etatCommande == null) {
      return CustomerOrderStatus.BROUILLON;
    }
    switch (etatCommande) {
      case VALIDEE:
        return CustomerOrderStatus.VALIDEE;
      case LIVREE:
        return CustomerOrderStatus.LIVREE;
      case EN_PREPARATION:
      default:
        return CustomerOrderStatus.BROUILLON;
    }
  }

  private EtatCommande toEtatCommande(CustomerOrderStatus status) {
    if (status == null) {
      return null;
    }
    switch (status) {
      case VALIDEE:
        return EtatCommande.VALIDEE;
      case LIVREE:
        return EtatCommande.LIVREE;
      default:
        return EtatCommande.EN_PREPARATION;
    }
  }

  private SiteDto resolveDefaultSite(Long idEntreprise) {
    if (idEntreprise == null) {
      throw new InvalidEntityException("La commande client n'est pas valide", ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
          List.of("Impossible de determiner l'entreprise de cette commande (aucune idEntreprise fournie ni resolue depuis l'utilisateur connecte)"));
    }
    Entreprise entreprise = entrepriseRepository.findById(idEntreprise).orElseThrow(
        () -> new InvalidEntityException("La commande client n'est pas valide", ErrorCodes.COMMANDE_CLIENT_NOT_VALID,
            List.of("Aucune entreprise avec l'ID = " + idEntreprise + " n'a ete trouvee dans la BDD")));
    return organizationService.ensureDefaultSite(entreprise.getOrganizationId());
  }

  private CommandeClientDto toCommandeClientDto(CustomerOrderDto order, Long fallbackIdEntreprise) {
    if (order == null) {
      return null;
    }
    Long idEntreprise = fallbackIdEntreprise != null ? fallbackIdEntreprise : resolveIdEntrepriseFromSite(order);
    return CommandeClientDto.builder()
        .id(order.getId())
        .code(order.getCode())
        .dateCommande(order.getOrderDate())
        .etatCommande(toEtatCommande(order.getStatus()))
        .client(toClientDto(order.getCustomerId()))
        .idEntreprise(idEntreprise)
        .build();
  }

  private ClientDto toClientDto(Long customerId) {
    if (customerId == null) {
      return null;
    }
    try {
      CustomerDto customer = customerService.findById(customerId);
      return ClientDto.builder()
          .id(customer.getId())
          .nom(customer.getNom())
          .prenom(customer.getPrenom())
          .adresse(customer.getAdresse())
          .photo(customer.getPhoto())
          .mail(customer.getMail())
          .numTel(customer.getNumTel())
          .build();
    } catch (EntityNotFoundException notFound) {
      return null;
    }
  }

  private Long resolveIdEntrepriseFromSite(CustomerOrderDto order) {
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
