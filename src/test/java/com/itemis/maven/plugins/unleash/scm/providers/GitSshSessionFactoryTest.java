package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Logger;

import org.eclipse.jgit.util.FS;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.base.Optional;
import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;

public class GitSshSessionFactoryTest {
  @Mock
  private FS mockFS;
  @Mock
  private ScmProviderInitialization mockInitialization;
  @Mock
  private Logger mockLogger;
  private GitSshSessionFactory sessionFactory;
  private JSch sshClient;
  private boolean connectorAvailable;

  private static String privateRSAPEMKey;
  private static String privateRSAOpenSSHKey;

  @BeforeClass
  public static void beforeClass() throws IOException {
    final Class<GitSshSessionFactoryTest> clazz = GitSshSessionFactoryTest.class;
    privateRSAPEMKey = GitScmTestUtil.readResourceFileToString(clazz, "passphrase_rsa_pem.key");
    privateRSAOpenSSHKey = GitScmTestUtil.readResourceFileToString(clazz, "passphrase_rsa_openssh.key");
  }

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    // reset this before every tests b/c it's static :(
    JSch.setConfig(new Hashtable<Object, Object>());

    this.sessionFactory = new GitSshSessionFactory(this.mockInitialization, this.mockLogger) {
      @Override
      boolean isConnectorAvailable() {
        return GitSshSessionFactoryTest.this.connectorAvailable;
      }
    };
  }

  @Test
  public void testNoPassphraseOrAgent() throws Exception {
    givenNoPassphraseIsPresent();
    givenNoAgentConnectorIsAvailable();
    whenCreateSshClient();
    thenIdentityRepositoryIsLocal();
    thenPreferredAuthenticationIsNotPublicKey();
  }

  @Test
  public void testUsePassphraseNoPrivateKey() throws Exception {
    givenAPassphraseIsPresent();
    givenNoPrivateKeyIsPresent();
    // Agent connector does not matter for this test case
    // givenAgentConnectorAvailable();
    whenCreateSshClient();
    thenIdentityRepositoryIsLocal();
  }

  @Test
  public void testUsePassphraseWithPrivatePEMKey() throws Exception {
    givenAPassphraseIsPresent();
    givenAPrivateKeyIsPresent(privateRSAPEMKey);
    // Agent connector does not matter for this test case
    // givenAgentConnectorAvailable();
    whenCreateSshClient();
    thenIdentityRepositoryIsLocal();
  }

  // TODO enable test after upgrade to more recent JGit SSH implementation
  // @Test
  public void testUsePassphraseWithPrivateOpenSSHKey() throws Exception {
    givenAPassphraseIsPresent();
    givenAPrivateKeyIsPresent(privateRSAOpenSSHKey);
    // Agent connector does not matter for this test case
    // givenAgentConnectorAvailable();
    whenCreateSshClient();
    thenIdentityRepositoryIsLocal();
  }

  @Test
  public void testUseSshAgent() throws Exception {
    if (GitScmTestUtil.isGithubAction()) {
      // TODO this test is not yet working on GHA context
      return;
    }
    givenNoPassphraseIsPresent();
    givenAgentConnectorAvailable();
    whenCreateSshClient();
    thenPreferredAuthenticationIsPublicKey();
    thenIdentityRepositoryIsRemote();
  }

  private void givenAgentConnectorAvailable() {
    this.connectorAvailable = true;
  }

  private void givenAPassphraseIsPresent() {
    Mockito.when(this.mockInitialization.getSshPrivateKeyPassphrase()).thenReturn(Optional.of("passphrase"));
  }

  private void givenNoAgentConnectorIsAvailable() {
    this.connectorAvailable = false;
  }

  private void givenNoPassphraseIsPresent() {
    Mockito.when(this.mockInitialization.getSshPrivateKeyPassphrase()).thenReturn(Optional.<String> absent());
  }

  private void givenNoPrivateKeyIsPresent() {
    Mockito.when(this.mockInitialization.getSshPrivateKey()).thenReturn(Optional.<String> absent());
  }

  private void givenAPrivateKeyIsPresent(final String privateKey) {
    Mockito.when(this.mockInitialization.getSshPrivateKey()).thenReturn(Optional.of(privateKey));
  }

  private void thenIdentityRepositoryIsLocal() {
    Assert.assertFalse(this.sshClient.getIdentityRepository() instanceof RemoteIdentityRepository);
  }

  private void thenIdentityRepositoryIsRemote() {
    Assert.assertTrue(this.sshClient.getIdentityRepository() instanceof RemoteIdentityRepository);
  }

  private void thenPreferredAuthenticationIsNotPublicKey() {
    // if this isn't explicitly set to 'publickey', no passphrase or agent were found
    Assert.assertNotEquals(GitSshSessionFactory.PUBLIC_KEY,
        JSch.getConfig(GitSshSessionFactory.PREFERRED_AUTHENTICATIONS));
  }

  private void thenPreferredAuthenticationIsPublicKey() {
    Assert.assertEquals(GitSshSessionFactory.PUBLIC_KEY,
        JSch.getConfig(GitSshSessionFactory.PREFERRED_AUTHENTICATIONS));
  }

  private void whenCreateSshClient() throws Exception {
    Mockito.when(this.mockFS.userHome()).thenReturn(null);
    this.sshClient = this.sessionFactory.createDefaultJSch(this.mockFS);
  }
}
