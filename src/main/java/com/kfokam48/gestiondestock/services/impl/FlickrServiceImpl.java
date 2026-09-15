package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.services.FlickrService;
import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.uploader.UploadMetaData;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FlickrServiceImpl implements FlickrService {

  private final String apiKey;

  private final String apiSecret;

  private final String appKey;

  private final String appSecret;

  private Flickr flickr;

  public FlickrServiceImpl(
      @Value("${flickr.apiKey}") String apiKey,
      @Value("${flickr.apiSecret}") String apiSecret,
      @Value("${flickr.appKey}") String appKey,
      @Value("${flickr.appSecret}") String appSecret) {
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.appKey = appKey;
    this.appSecret = appSecret;
  }

  @Override
  @SneakyThrows
  public String savePhoto(InputStream photo, String title) {
    connect();
    UploadMetaData uploadMetaData = new UploadMetaData();
    uploadMetaData.setTitle(title);

    String photoId = flickr.getUploader().upload(photo, uploadMetaData);
    return flickr.getPhotosInterface().getPhoto(photoId).getMedium640Url();
  }

  private void connect() throws InterruptedException, ExecutionException, IOException, FlickrException {
    flickr = new Flickr(apiKey, apiSecret, new REST());
    Auth auth = new Auth();
    auth.setPermission(Permission.READ);
    auth.setToken(appKey);
    auth.setTokenSecret(appSecret);
    RequestContext requestContext = RequestContext.getRequestContext();
    requestContext.setAuth(auth);
    flickr.setAuth(auth);
  }

}
