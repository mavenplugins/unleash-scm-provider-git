package com.itemis.maven.plugins.unleash.scm.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.FS;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;

public class GitSshSessionFactoryTest {
  @Mock
  private FS mockFS;
  @Mock
  private ScmProviderInitialization mockInitialization;
  // @Mock
  // private Logger mockLogger;

  private ScmProviderGit scmProviderGit;

  private static String privateTestRepoOpenSSHKey;
  private static String privateTestRepoPEMKey;
  private static String privateTestRepoPassphrase = StringUtils
      .defaultIfBlank(System.getenv("TEST_REPO_KEY_PASSPHRASE"), StringUtils.EMPTY);

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(GitSshSessionFactoryTest.class);

  private static final String FILE_BASE_PATH = "target/test-classes/com/itemis/maven/plugins/unleash/scm/providers/GitSshSessionFactoryTest/";
  private static final String FILE_BASE_URL = "file:" + FILE_BASE_PATH;
  private static final String FILE_BASE_PATH_SSH = FILE_BASE_PATH + "_ssh/";

  @Rule
  public MonitorRule monitor = new MonitorRule(LOGGER);

  @BeforeClass
  public static void beforeClass() throws IOException {
    final Class<GitSshSessionFactoryTest> clazz = GitSshSessionFactoryTest.class;
    privateTestRepoOpenSSHKey = GitScmTestUtil.readResourceFileToString(clazz,
        "git_ssh_test_repo_ecdsa521_openssh.key");
    privateTestRepoPEMKey = GitScmTestUtil.readResourceFileToString(clazz, "git_ssh_test_repo_ecdsa521_pem.key");
  }

  @Before
  public void beforeMethod() {
    // Run this test only on GHA or if key passphrase is configured
    Assume.assumeTrue(GitScmTestUtil.isGithubAction() || StringUtils.isNotBlank(privateTestRepoPassphrase));
    // Init before each test method
    MockitoAnnotations.initMocks(this);
    givenLogger();
    givenNoUsernameIsPresent();
    givenNoKnownHostsFile();
    givenKnownHostsFile(FILE_BASE_PATH_SSH + "known_hosts");
    givenNoSSHConfigFile();
    givenWorkingDir("target/repo_workingdir");
  }

  @Test
  public void testSSHConnectionToTestRepoWithPEMKeyFile() throws Exception {
    givenAPassphraseIsPresent(privateTestRepoPassphrase);
    // givenAPrivateKeyIsPresent(privateTestRepoPEMKey);
    givenAPrivateKeyIsPresent(FILE_BASE_URL + "git_ssh_test_repo_ecdsa521_pem.key");
    whenCreateScmProviderGit();
    thenTestConnectionWorks("github.com");
  }

  @Test
  public void testSSHConnectionToTestRepoWithOpenSSHKeyFile() throws Exception {
    givenAPassphraseIsPresent(privateTestRepoPassphrase);
    givenAPrivateKeyIsPresent(FILE_BASE_URL + "git_ssh_test_repo_ecdsa521_openssh.key");
    whenCreateScmProviderGit();
    thenTestConnectionWorks("github.com");
  }

  @Test
  public void testSSHConnectionToTestRepoWithPEMKeyRaw() throws Exception {
    givenAPassphraseIsPresent(privateTestRepoPassphrase);
    givenAPrivateKeyIsPresent(privateTestRepoPEMKey);
    whenCreateScmProviderGit();
    thenTestConnectionWorks("github.com");
  }

  @Test
  public void testSSHConnectionToTestRepoWithOpenSSHKeyRaw() throws Exception {
    givenAPassphraseIsPresent(privateTestRepoPassphrase);
    givenAPrivateKeyIsPresent(privateTestRepoOpenSSHKey);
    whenCreateScmProviderGit();
    thenTestConnectionWorks("github.com");
  }

  @Test
  public void testSSHConnectionToTestRepoNoKeyConfigured() throws Exception {
    givenAPassphraseIsPresent(privateTestRepoPassphrase);
    givenNoPrivateKeyIsPresent();
    givenKnownHostsFile(FILE_BASE_PATH_SSH + "known_hosts");
    givenSSHConfigFile(FILE_BASE_PATH_SSH + "config");
    whenCreateScmProviderGit();
    thenKnownHostsFileExists();
    thenSSHConfigFileExists();
    thenTestConnectionWorks("github.com-mavenplugins-git_ssh_test_repo");
  }

  private void givenNoKnownHostsFile() {
    givenKnownHostsFile(null);
  }

  private void givenKnownHostsFile(String filePath) {
    final String propName = ScmProviderAwareSshdSessionFactory.SCM_GIT_SSH_KNOWN_HOSTS_FILE_PROP;
    if (filePath != null) {
      System.setProperty(propName, filePath);
    } else {
      System.clearProperty(propName);
    }
  }

  private void givenNoSSHConfigFile() {
    givenSSHConfigFile(null);
  }

  private void givenSSHConfigFile(String filePath) {
    final String propName = ScmProviderAwareSshdSessionFactory.SCM_GIT_SSH_CONFIG_FILE_PROP;
    if (filePath != null) {
      System.setProperty(propName, filePath);
    } else {
      System.clearProperty(propName);
    }
  }

  // private void givenAgentConnectorAvailable() {
  // this.connectorAvailable = true;
  // }

  @SuppressWarnings("unused")
  private void givenAPassphraseIsPresent() {
    givenAPassphraseIsPresent("passphrase");
  }

  private void givenAPassphraseIsPresent(String passphrase) {
    Mockito.when(this.mockInitialization.getSshPrivateKeyPassphrase()).thenReturn(Optional.of(passphrase));
  }

  // private void givenNoAgentConnectorIsAvailable() {
  // this.connectorAvailable = false;
  // }

  @SuppressWarnings("unused")
  private void givenNoPassphraseIsPresent() {
    Mockito.when(this.mockInitialization.getSshPrivateKeyPassphrase()).thenReturn(Optional.<String> absent());
  }

  private void givenNoPrivateKeyIsPresent() {
    Mockito.when(this.mockInitialization.getSshPrivateKey()).thenReturn(Optional.<String> absent());
  }

  private void givenAPrivateKeyIsPresent(final String privateKey) {
    Mockito.when(this.mockInitialization.getSshPrivateKey()).thenReturn(Optional.of(privateKey));
  }

  private void givenNoUsernameIsPresent() {
    Mockito.when(this.mockInitialization.getUsername()).thenReturn(Optional.<String> absent());
  }

  private void givenLogger() {
    // Mockito.when(this.mockInitialization.getLogger()).thenReturn(Optional.of(this.mockLogger));
    Mockito.when(this.mockInitialization.getLogger()).thenReturn(Optional.absent());
  }

  private void givenWorkingDir(final String workingDir) {
    Mockito.when(this.mockInitialization.getWorkingDirectory()).thenReturn(new File(workingDir));
  }

  private void whenCreateScmProviderGit() {
    this.scmProviderGit = new ScmProviderGit();
    this.scmProviderGit.initialize(this.mockInitialization);
  }

  private void thenSSHConfigFileExists() {
    assertTrue(new File(System.getProperty(ScmProviderAwareSshdSessionFactory.SCM_GIT_SSH_CONFIG_FILE_PROP)).exists());
  }

  private void thenKnownHostsFileExists() {
    assertTrue(
        new File(System.getProperty(ScmProviderAwareSshdSessionFactory.SCM_GIT_SSH_KNOWN_HOSTS_FILE_PROP)).exists());
  }

  private void thenTestConnectionWorks(String hostName) {
    final String repoURL = "ssh://git@" + hostName + "/mavenplugins/git_ssh_test_repo.git";
    final Collection<Ref> result = this.scmProviderGit.testConnection(repoURL);
    assertEquals(1, result.size());
    LOGGER.info(repoURL + " - ref: " + result.stream().findFirst().get().getName());
  }
}
