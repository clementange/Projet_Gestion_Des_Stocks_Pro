package com.kfokam48.gestiondestock.media.application;

import java.io.InputStream;

/**
 * Capacite transversale de stockage objet (Phase 4c, docs/phase-4c-report.md) : upload seul, sans
 * connaissance d'aucune entite metier. Chaque module attache lui-meme l'URL retournee a son
 * entite (voir {@code catalog.ArticleService.updatePhoto}, {@code identity.UserService.updatePhoto},
 * etc.) - ce service ne fait jamais l'inverse.
 */
public interface MediaStorageService {

  String upload(InputStream content, long size, String contentType, String originalFilename);

}
