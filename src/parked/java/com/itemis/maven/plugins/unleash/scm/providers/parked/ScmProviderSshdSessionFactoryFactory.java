/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.itemis.maven.plugins.unleash.scm.providers.parked;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.IdentityPasswordProvider;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;
import com.itemis.maven.plugins.unleash.scm.providers.logging.JavaLoggerSLF4JAdapter;
import com.itemis.maven.plugins.unleash.scm.providers.util.GitUtil;

/**
 * This class is responsible for creating a {@link SshdSessionFactory}.
 * It is used to authenticate with a remote Git repository using SSH considering the settings from
 * {@link ScmProviderInitialization}.
 *
 * @deprecated Since this is parked for knowledge lookup only
 */
@Deprecated
public class ScmProviderSshdSessionFactoryFactory {
  private final ScmProviderInitialization initialization;
  private PublicKeyEntry publicGitHubSshKeyFingerprint = null;

  // This is the SSH fingerprint for github.com
  // It is used to verify the server's identity when connecting via SSH.
  // The fingerprint is the Base64-encoded SHA-256 hash of the server's public key.
  // More information can be found at
  // https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints
  // IMPORTANT: Because this fingerprint is public knowledge, it is not a secret.
  private static final String GITHUB_ECDSA_SSH_FINGERPRINT;

  static {
    // GITHUB_ECDSA_SSH_FINGERPRINT = "ecdsa-sha2-nistp256
    // AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBEmKSENjQEezOmxkZMy7opKgwFB9nkt5YRrYMjNuG5N87uRgg6CLrbo5wAdT/y6v0mKV0U2w0WZ2YB/++Tpockg=";
    GITHUB_ECDSA_SSH_FINGERPRINT = null;
  }

  public ScmProviderSshdSessionFactoryFactory(final ScmProviderInitialization initialization) {
    this.initialization = initialization;
    if (GITHUB_ECDSA_SSH_FINGERPRINT != null) {
      this.publicGitHubSshKeyFingerprint = PublicKeyEntry.parsePublicKeyEntry(GITHUB_ECDSA_SSH_FINGERPRINT);
    }
  }

  /**
   * Builds a custom SSHD session factory.
   *
   * @return a SshdSessionFactory
   */
  public SshdSessionFactory buildSshdSessionFactory() {
    final String sshPrivateKey = getEffectivePrivateKey();
    if (StringUtils.isBlank(sshPrivateKey)) {
      return new ScmProviderNoPrivateKeyDefinedSshdSessionFactory(this.initialization);
    }
    final Iterable<KeyPair> keyPairs = loadKeyPairs(sshPrivateKey,
        this.initialization.getSshPrivateKeyPassphrase().or(StringUtils.EMPTY));
    final Path tempDirectoryForSshSessionFactory = createTemporaryDirectory();
    return new SshdSessionFactoryBuilder().setPreferredAuthentications(GitUtil.SSH_PUBLIC_KEY_AUTHENTICATION)
        .setDefaultKeysProvider(ignoredSshDirBecauseWeUseAnInMemorySetOfKeyPairs -> keyPairs)
        // A requirement of the SshdSessionFactoryBuilder is
        // to set the home directory and ssh directory
        // despite providing our SSH key pair programmatically.
        // See: https://github.com/eclipse-jgit/jgit/issues/89
        .setHomeDirectory(tempDirectoryForSshSessionFactory.toFile())
        .setSshDirectory(tempDirectoryForSshSessionFactory.toFile())
        .setConfigStoreFactory((ignoredHomeDir, ignoredConfigFile, ignoredLocalUserName) -> null)
        .setConfigFile(ignoredSshDir -> null) // The function may return null, in which case no SSH config file will be
                                              // used.
        .setServerKeyDatabase((ignoredHomeDir, ignoredSshDir) -> new ServerKeyDatabase() {
          @Override
          public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
            return Collections.emptyList();
          }

          /**
           * This method is used to compare the server's known host public key with the provided remote servers public
           * key fingerprint.
           * If the keys match, the connection is accepted.
           *
           * @param connectAddress the address to connect to
           * @param remoteAddress  the remote address
           * @param serverKey      the server's public key
           * @param config         the configuration
           * @param provider       the credentials provider
           * @return true if the server's public key matches the GitHub public key fingerprint
           */
          @Override
          public boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey,
              Configuration config, CredentialsProvider provider) {
            if (ScmProviderSshdSessionFactoryFactory.this.publicGitHubSshKeyFingerprint != null) {
              PublicKey gitHubPublicKey;
              try {
                gitHubPublicKey = ScmProviderSshdSessionFactoryFactory.this.publicGitHubSshKeyFingerprint
                    .resolvePublicKey(null, null, PublicKeyEntryResolver.IGNORING);
              } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException(e);
              }
              return KeyUtils.compareKeys(serverKey, gitHubPublicKey);
            }
            return true;
          }
        }).build(new JGitKeyCache());
  }

  private String getEffectivePrivateKey() {
    final String sshPrivateKey = this.initialization.getSshPrivateKey().or(StringUtils.EMPTY);
    if (StringUtils.isBlank(sshPrivateKey)) {
      return StringUtils.EMPTY;
    }
    try {
      // Try if it is a file URL
      final URL url = new URL(sshPrivateKey);
      if (StringUtils.isNotBlank(url.getFile())) {
        try {
          return new String(Files.readAllBytes(Paths.get(url.getFile())), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException("Failed to read SSH key file: " + url.getFile(), e);
        }
      }
    } catch (MalformedURLException e) {
      // nothing to do
    }
    return sshPrivateKey;
  }

  /**
   * Loads the SSH private key from the provided content.
   *
   * @param privateKeyContent the content of the private key
   * @param passphrase        the passphrase for the private key
   * @return iterable of KeyPair
   */
  private Iterable<KeyPair> loadKeyPairs(@Nonnull String privateKeyContent, @Nullable String passphrase) {
    Iterable<KeyPair> keyPairs;
    try {
      keyPairs = SecurityUtils.loadKeyPairIdentities(null, null, new ByteArrayInputStream(privateKeyContent.getBytes()),
          (session, resourceKey, retryIndex) -> passphrase);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to load ssh private key", e);
    }
    if (keyPairs == null) {
      throw new IllegalArgumentException("Failed to load ssh private key");
    }
    return keyPairs;
  }

  private Path createTemporaryDirectory() {
    Path temporaryDirectory;
    try {
      temporaryDirectory = Files.createTempDirectory("scm-provider-git-temp-dir");
      temporaryDirectory.toFile().deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temporary directory", e);
    }
    return temporaryDirectory;
  }

  /**
   * {@link SshdSessionFactory} considering the settings from {@link ScmProviderInitialization}.
   */
  public class ScmProviderNoPrivateKeyDefinedSshdSessionFactory extends SshdSessionFactory {
    private final ScmProviderInitialization initialization;
    private final Logger logger;

    public ScmProviderNoPrivateKeyDefinedSshdSessionFactory(final ScmProviderInitialization initialization) {
      this.initialization = initialization;
      this.logger = initialization.getLogger().or(JavaLoggerSLF4JAdapter.getLogger(getClass()));
    }

    @Override
    protected KeyPasswordProvider createKeyPasswordProvider(CredentialsProvider provider) {
      final String passPhrase = this.initialization.getSshPrivateKeyPassphrase().orNull();
      if (passPhrase != null) {
        return new IdentityPasswordProvider(provider) {
          @Override
          public char[] getPassphrase(URIish uri, int attempt) throws IOException {
            if (attempt > 0) {
              throw new IOException("Passphrase was not correct in first attempt - aborting further attempts!");
            }
            ScmProviderNoPrivateKeyDefinedSshdSessionFactory.this.logger.fine("Using passphrase configured");
            return passPhrase.toCharArray();
          }
        };
      }
      return super.createKeyPasswordProvider(provider);
    }

    @Override
    protected String getDefaultPreferredAuthentications() {
      return GitUtil.SSH_PUBLIC_KEY_AUTHENTICATION;
    }

  }
}
