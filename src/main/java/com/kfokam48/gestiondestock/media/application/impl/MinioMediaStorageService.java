package com.kfokam48.gestiondestock.media.application.impl;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.media.application.MediaStorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Phase 4c : remplace FlickrServiceImpl. Le bucket est cree et rendu public-read au demarrage
 * (equivalent des URLs Flickr publiques deja consommees telles quelles par le reste de
 * l'application - aucun mecanisme d'URL signee/expirante n'existait avant, on ne l'introduit pas
 * ici). Nom d'objet genere ({@code UUID + extension}), jamais le nom de fichier original -
 * evite toute collision entre tenants et toute injection de chemin.
 */
@Service
@Slf4j
public class MinioMediaStorageService implements MediaStorageService {

  private final MinioClient client;
  private final String bucket;
  private final String endpoint;

  public MinioMediaStorageService(
      @Value("${minio.endpoint}") String endpoint,
      @Value("${minio.access-key}") String accessKey,
      @Value("${minio.secret-key}") String secretKey,
      @Value("${minio.bucket}") String bucket) {
    this.endpoint = endpoint;
    this.bucket = bucket;
    this.client = MinioClient.builder()
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build();
  }

  @PostConstruct
  void ensureBucketExists() {
    try {
      boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(publicReadPolicy()).build());
      }
    } catch (Exception ex) {
      log.error("Impossible d'initialiser le bucket MinIO {}", bucket, ex);
      throw new IllegalStateException("Impossible d'initialiser le stockage media", ex);
    }
  }

  private String publicReadPolicy() {
    return "{"
        + "\"Version\":\"2012-10-17\","
        + "\"Statement\":[{"
        + "\"Effect\":\"Allow\","
        + "\"Principal\":{\"AWS\":[\"*\"]},"
        + "\"Action\":[\"s3:GetObject\"],"
        + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]"
        + "}]}";
  }

  @Override
  public String upload(InputStream content, long size, String contentType, String originalFilename) {
    String objectKey = UUID.randomUUID() + extensionOf(originalFilename);
    try {
      client.putObject(PutObjectArgs.builder()
          .bucket(bucket)
          .object(objectKey)
          .stream(content, size, -1)
          .contentType(contentType)
          .build());
    } catch (Exception ex) {
      log.error("Erreur lors de l'upload media", ex);
      throw new InvalidOperationException("Erreur lors de l'enregistrement du media", ErrorCodes.UPDATE_PHOTO_EXCEPTION);
    }
    return endpoint + "/" + bucket + "/" + objectKey;
  }

  private String extensionOf(String originalFilename) {
    if (!StringUtils.hasLength(originalFilename) || !originalFilename.contains(".")) {
      return "";
    }
    return originalFilename.substring(originalFilename.lastIndexOf('.'));
  }
}
