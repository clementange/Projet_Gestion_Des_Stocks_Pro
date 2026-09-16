package com.kfokam48.gestiondestock.media.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.media.application.MediaStorageService;
import com.kfokam48.gestiondestock.media.application.dto.MediaUploadResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phase 4c : remplace {@code /save/{id}/{title}/{context}} (StrategyPhotoContext, supprime - voir
 * docs/phase-4c-report.md). Upload seul, decouple de toute entite : l'appelant recupere l'URL
 * puis l'attache lui-meme via l'endpoint {@code POST /{ressource}/{id}/photo} du module concerne.
 * {@code authenticated()} seul (regle par defaut de SecurityConfiguration), meme posture que les
 * autres endpoints neufs de la Phase 4.
 */
@Tag(name = "media")
@RestController
public class MediaController {

  private MediaStorageService mediaStorageService;

  @Autowired
  public MediaController(MediaStorageService mediaStorageService) {
    this.mediaStorageService = mediaStorageService;
  }

  @PostMapping(value = APP_ROOT + "/media/upload", produces = MediaType.APPLICATION_JSON_VALUE)
  public MediaUploadResponse upload(@RequestPart("file") MultipartFile file) throws IOException {
    String url = mediaStorageService.upload(file.getInputStream(), file.getSize(), file.getContentType(), file.getOriginalFilename());
    return new MediaUploadResponse(url);
  }
}
