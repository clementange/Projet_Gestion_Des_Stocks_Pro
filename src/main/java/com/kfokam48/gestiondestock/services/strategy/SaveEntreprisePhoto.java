package com.kfokam48.gestiondestock.services.strategy;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.services.FlickrService;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.flickr4java.flickr.FlickrException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 4b : rebranche sur tenant.application.TenantService (successeur de EntrepriseService,
// supprime - voir docs/phase-4b-report.md). TenantService.save() est desormais un upsert plat
// sans orchestration (Organization/Site/User/RBAC), donc cet appel ne redeclenche plus toute
// l'inscription a chaque changement de photo - correctif structurel obtenu en consequence de la
// separation TenantService/TenantRegistrationService, pas un correctif cible.
@Service("entrepriseStrategy")
@Slf4j
public class SaveEntreprisePhoto implements Strategy<TenantDto> {

  private FlickrService flickrService;
  private TenantService tenantService;

  @Autowired
  public SaveEntreprisePhoto(FlickrService flickrService, TenantService tenantService) {
    this.flickrService = flickrService;
    this.tenantService = tenantService;
  }

  @Override
  public TenantDto savePhoto(Long id, InputStream photo, String titre) throws FlickrException {
    TenantDto tenant = tenantService.findById(id);
    String urlPhoto = flickrService.savePhoto(photo, titre);
    if (!StringUtils.hasLength(urlPhoto)) {
      throw new InvalidOperationException("Erreur lors de l'enregistrement de photo de l'entreprise", ErrorCodes.UPDATE_PHOTO_EXCEPTION);
    }
    tenant.setPhoto(urlPhoto);
    return tenantService.save(tenant);
  }
}
