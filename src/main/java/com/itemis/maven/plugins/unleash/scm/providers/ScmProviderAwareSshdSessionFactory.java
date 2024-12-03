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
package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.sshd.OpenSshServerKeyDatabase;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.IdentityPasswordProvider;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;

import com.google.common.base.Optional;
import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;
import com.itemis.maven.plugins.unleash.scm.providers.logging.JavaLoggerSLF4JAdapter;
import com.itemis.maven.plugins.unleash.scm.providers.sshd.StringKeyPairProvider;
import com.itemis.maven.plugins.unleash.scm.providers.util.GitUtil;

/**
 * {@link SshdSessionFactory} considering the settings from {@link ScmProviderInitialization}.
 *
 */
public class ScmProviderAwareSshdSessionFactory extends SshdSessionFactory {

  public static final String SCM_GIT_SSH_CONFIG_FILE_PROP = "SCM_GIT_SSH_CONFIG_FILE";
  public static final String SCM_GIT_SSH_KNOWN_HOSTS_FILE_PROP = "SCM_GIT_SSH_KNOWN_HOSTS_FILE";

  private final Logger logger;
  private final String sshPrivateKey;
  private final String sshPrivateKeyPassphrase;

  public ScmProviderAwareSshdSessionFactory(final ScmProviderInitialization initialization) {
    super(new JGitKeyCache(), new DefaultProxyDataFactory());
    this.logger = initialization.getLogger().or(JavaLoggerSLF4JAdapter.getLogger(getClass()));
    this.sshPrivateKey = getEffectiveSshPrivateKey(initialization.getSshPrivateKey());
    this.sshPrivateKeyPassphrase = initialization.getSshPrivateKeyPassphrase().or(StringUtils.EMPTY);
    if (StringUtils.isNotBlank(this.sshPrivateKeyPassphrase)) {
      this.logger.info("SSH passphrase is configured");
    }
  }

  @Override
  protected SshConfigStore createSshConfigStore(@NonNull File homeDir, File configFile, String localUserName) {
    final String configFileName = System.getProperty(SCM_GIT_SSH_CONFIG_FILE_PROP);
    if (StringUtils.isNotBlank(configFileName)) {
      configFile = new File(configFileName);
    }
    this.logger.info("Using SSH config file: " + configFile);
    return super.createSshConfigStore(homeDir, configFile, localUserName);
  }

  @Override
  protected List<Path> getDefaultKnownHostsFiles(@NonNull File sshDir) {
    final String knownHostsFile = System.getProperty(SCM_GIT_SSH_KNOWN_HOSTS_FILE_PROP);
    if (StringUtils.isNotBlank(knownHostsFile)) {
      final Path knwonHostsFilePath = Paths.get(knownHostsFile);
      this.logger.info("Using known hosts file: " + knwonHostsFilePath.toAbsolutePath());
      return Arrays.asList(knwonHostsFilePath);
    }
    return super.getDefaultKnownHostsFiles(sshDir);
  }

  @Override
  public ServerKeyDatabase createServerKeyDatabase(@NonNull File homeDir, @NonNull File sshDir) {
    // Don't ask the user about a new file
    return new OpenSshServerKeyDatabase(false, getDefaultKnownHostsFiles(sshDir)) {

      @Override
      public boolean accept(@NonNull String connectAddress, @NonNull InetSocketAddress remoteAddress,
          @NonNull PublicKey serverKey, @NonNull Configuration config, CredentialsProvider provider) {
        ScmProviderAwareSshdSessionFactory.this.logger
            .fine("Fingerprint of " + connectAddress + ": " + KeyUtils.getFingerPrint(serverKey));
        return super.accept(connectAddress, remoteAddress, serverKey, config, provider);
      }

    };
  }

  @Override
  protected Iterable<KeyPair> getDefaultKeys(@NonNull File sshDir) {
    if (StringUtils.isNotBlank(this.sshPrivateKey)) {
      return new StringKeyPairProvider(this.sshPrivateKey, this.sshPrivateKeyPassphrase).loadKeys(null);
    }
    return super.getDefaultKeys(sshDir);
  }

  @Override
  protected KeyPasswordProvider createKeyPasswordProvider(CredentialsProvider provider) {
    return new IdentityPasswordProvider(provider) {
      @Override
      public char[] getPassphrase(URIish uri, int attempt) throws IOException {
        if (attempt > 0) {
          throw new IOException("SSH passphrase was not correct in first attempt - aborting further attempts!");
        }
        ScmProviderAwareSshdSessionFactory.this.logger.fine("Using passphrase configured");
        return ScmProviderAwareSshdSessionFactory.this.sshPrivateKeyPassphrase.toCharArray();
      }
    };
  }

  @Override
  protected String getDefaultPreferredAuthentications() {
    return GitUtil.SSH_PUBLIC_KEY_AUTHENTICATION;
  }

  /**
   * Check if private key is to be read from a file, if SSH private key is configured as a file URL.
   *
   * @param optSshPrivateKey
   * @return
   */
  private String getEffectiveSshPrivateKey(final Optional<String> optSshPrivateKey) {
    final String sshPrivateKey = optSshPrivateKey.or(StringUtils.EMPTY);
    if (StringUtils.isBlank(sshPrivateKey)) {
      return StringUtils.EMPTY;
    }
    try {
      // Try if it is a file URL
      final URL url = new URL(sshPrivateKey);
      this.logger.info("SSH private key configured as URL: " + url);
      if ("file".equalsIgnoreCase(url.getProtocol())) {
        try {
          final Path filePath = Paths.get(url.getPath());
          this.logger.info("Reading SSH key from file " + filePath);
          return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException("Failed to read SSH key from file: " + url.getFile(), e);
        }
      } else {
        throw new IllegalArgumentException("SSH key URL protocol not supported for URL: " + url);
      }
    } catch (MalformedURLException e) {
      // nothing to do
      this.logger.info("SSH private key configured as String");
    }
    return sshPrivateKey;
  }

}
