package com.itemis.maven.plugins.unleash.scm.providers.sshd;

import java.security.KeyPair;
import java.util.Collections;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.AbstractResourceKeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.io.resource.IoResource;

/**
 * Use {@link StringKeyPairProvider.StringWrapper} as the {@link IoResource} wrapped data content to never show the
 * inner string in generic logging.
 *
 * @author mhoffrog
 */
public class StringKeyPairProvider extends AbstractResourceKeyPairProvider<StringKeyPairProvider.StringWrapper> {

  private static final String DEFAULT_RESOURCE_NAME = "unleash_scm_git_private_key_configured";

  private final String resourceName;
  private final StringWrapper privateKeyWrapper;

  public StringKeyPairProvider(String privateKey, String privateKeyPassphrase) {
    this(privateKey, privateKeyPassphrase, DEFAULT_RESOURCE_NAME);
  }

  public StringKeyPairProvider(String privateKey, String privateKeyPassphrase, String resourceName) {
    this.resourceName = resourceName;
    this.privateKeyWrapper = new StringWrapper(privateKey, resourceName);
    setPasswordFinder(FilePasswordProvider.of(privateKeyPassphrase));
  }

  @Override
  public Iterable<KeyPair> loadKeys(SessionContext session) {
    return loadKeys(session, Collections.singletonList(this.privateKeyWrapper));
  }

  @Override
  protected IoResource<StringWrapper> getIoResource(SessionContext session, StringWrapper resource) {
    return resource == null ? null : new StringIoResource(this.resourceName, resource);
  }

  /**
   * Wrap a String as inner data to never show the inner string in generic logging.
   *
   * @author mhoffrog
   */
  static class StringWrapper {
    private final String value;
    private final String resourceName;

    private StringWrapper(String value, String resourceName) {
      super();
      this.value = value;
      this.resourceName = resourceName;
    }

    public String getValue() {
      return this.value;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "[" + this.resourceName + "]";
    }

  }

}
