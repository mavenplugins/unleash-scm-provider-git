package com.itemis.maven.plugins.unleash.scm.providers.sshd;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.sshd.common.util.io.resource.IoResource;

import com.itemis.maven.plugins.unleash.scm.providers.sshd.StringKeyPairProvider.StringWrapper;

/**
 * Use {@link StringWrapper} as the {@link IoResource} wrapped data content to never show the inner string in generic
 * logging.
 *
 * @author mhoffrog
 */
class StringIoResource implements IoResource<StringWrapper> {
  private final String resourceName;
  private final StringWrapper stringWrapper;
  private final Charset stringValueCharset;

  public StringIoResource(String resourceName, StringWrapper stringWrapper) {
    this(resourceName, stringWrapper, StandardCharsets.UTF_8);
  }

  public StringIoResource(String resourceName, StringWrapper stringWrapper, Charset stringValueCharset) {
    this.resourceName = resourceName;
    this.stringWrapper = stringWrapper;
    this.stringValueCharset = stringValueCharset;
  }

  @Override
  public Class<StringWrapper> getResourceType() {
    return StringWrapper.class;
  }

  @Override
  public StringWrapper getResourceValue() {
    return this.stringWrapper;
  }

  @Override
  public String getName() {
    return this.resourceName;
  }

  @Override
  public InputStream openInputStream() {
    return new ByteArrayInputStream(getResourceValue().getValue().getBytes(this.stringValueCharset));
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + getName() + "]";
  }

}
