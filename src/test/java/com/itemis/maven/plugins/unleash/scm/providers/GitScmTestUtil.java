package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

public class GitScmTestUtil {

  private GitScmTestUtil() {
    // not supposed to be instantiated
  }

  public static String readResourceFileToString(Class<?> clazz, String fileName) throws IOException {
    URL url = clazz.getResource(clazz.getSimpleName() + "/" + fileName);
    File f;
    try {
      f = new File(url.toURI());
    } catch (URISyntaxException e) {
      f = new File(url.getPath());
    }
    return new String(Files.readAllBytes(f.toPath()));
  }

}
