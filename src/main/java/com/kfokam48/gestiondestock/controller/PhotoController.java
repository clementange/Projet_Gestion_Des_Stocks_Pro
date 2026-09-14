package com.kfokam48.gestiondestock.controller;

import com.kfokam48.gestiondestock.controller.api.PhotoApi;
import com.kfokam48.gestiondestock.services.strategy.StrategyPhotoContext;
import com.flickr4java.flickr.FlickrException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PhotoController implements PhotoApi {

  private StrategyPhotoContext strategyPhotoContext;

  @Autowired
  public PhotoController(StrategyPhotoContext strategyPhotoContext) {
    this.strategyPhotoContext = strategyPhotoContext;
  }

  @Override
  public Object savePhoto(String context, Long id, MultipartFile photo, String title) throws IOException, FlickrException {
    return strategyPhotoContext.savePhoto(context, id, photo.getInputStream(), title);
  }
}
