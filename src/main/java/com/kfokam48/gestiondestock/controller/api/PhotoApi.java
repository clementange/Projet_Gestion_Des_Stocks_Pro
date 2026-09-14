package com.kfokam48.gestiondestock.controller.api;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.flickr4java.flickr.FlickrException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "photos")
public interface PhotoApi {

  @PostMapping(APP_ROOT + "/save/{id}/{title}/{context}")
  Object savePhoto(@PathVariable("context") String context, @PathVariable("id") Long id,@RequestPart("file") MultipartFile photo, @PathVariable(
      "title") String title) throws IOException,
      FlickrException;

}
