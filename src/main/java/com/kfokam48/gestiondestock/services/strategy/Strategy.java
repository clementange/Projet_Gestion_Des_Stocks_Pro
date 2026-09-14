package com.kfokam48.gestiondestock.services.strategy;

import com.flickr4java.flickr.FlickrException;
import java.io.InputStream;

public interface Strategy<T> {

  T savePhoto(Long id, InputStream photo, String titre) throws FlickrException;

}
