package com.itemis.maven.plugins.unleash.scm.providers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.DeleteTagCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;
import com.itemis.maven.plugins.unleash.scm.ScmProvider;
import com.itemis.maven.plugins.unleash.scm.ScmProviderInitialization;
import com.itemis.maven.plugins.unleash.scm.annotations.ScmProviderType;
import com.itemis.maven.plugins.unleash.scm.providers.cdi.IGitCommandFactory;
import com.itemis.maven.plugins.unleash.scm.providers.merge.UnleashGitFullMergeStrategy;
import com.itemis.maven.plugins.unleash.scm.providers.util.GitUtil;
import com.itemis.maven.plugins.unleash.scm.requests.BranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CheckoutRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest.Builder;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteBranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteTagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DiffRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DiffRequest.DiffType;
import com.itemis.maven.plugins.unleash.scm.requests.HistoryRequest;
import com.itemis.maven.plugins.unleash.scm.requests.PushRequest;
import com.itemis.maven.plugins.unleash.scm.requests.RevertCommitsRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.UpdateRequest;
import com.itemis.maven.plugins.unleash.scm.results.DiffObject;
import com.itemis.maven.plugins.unleash.scm.results.DiffResult;
import com.itemis.maven.plugins.unleash.scm.results.HistoryCommit;
import com.itemis.maven.plugins.unleash.scm.results.HistoryResult;
import com.itemis.maven.plugins.unleash.scm.utils.FileToRelativePath;

import jakarta.inject.Inject;

@ScmProviderType("git")
public class ScmProviderGit implements ScmProvider {
  private static final String LOG_PREFIX = "Git - ";

  private Logger log;
  private Git git;
  private PersonIdent personIdent;
  private CredentialsProvider credentialsProvider;
  private SshSessionFactory sshSessionFactory;
  private File workingDir;
  private String workingDirParentToGitWorkTree;
  private List<String> additionalThingsToPush;
  private GitUtil util;
  @Inject
  private IGitCommandFactory gitCommandFactory;

  @Override
  public void initialize(final ScmProviderInitialization initialization) {
    this.log = initialization.getLogger().or(Logger.getLogger(ScmProvider.class.getName()));
    this.workingDir = initialization.getWorkingDirectory();
    this.additionalThingsToPush = Lists.newArrayList();

    if (this.workingDir.exists() && this.workingDir.isDirectory() && this.workingDir.list().length > 0) {
      try {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = builder.findGitDir(this.workingDir).build();
        this.workingDirParentToGitWorkTree = new FileToRelativePath(repo.getWorkTree()).apply(this.workingDir);
        this.git = Git.wrap(repo);
        this.personIdent = new PersonIdent(repo);
        this.util = new GitUtil(this.git);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (initialization.getUsername().isPresent()) {
      this.credentialsProvider = new UsernamePasswordCredentialsProvider(initialization.getUsername().get(),
          initialization.getPassword().or(""));
    }
    this.sshSessionFactory = new GitSshSessionFactory(initialization, this.log);

    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "JGit version: " + Git.class.getPackage().getImplementationVersion());
      this.log.info(LOG_PREFIX + "GitCommandFactory: " + this.gitCommandFactory.getClass().getName());
    }
    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("WorkingDir Info:\n");
      message.append("\t- GIT_DIR: \"").append(this.git.getRepository().getDirectory().getAbsolutePath())
          .append("\"\n");
      message.append("\t- GIT_WORKTREE: \"").append(this.git.getRepository().getWorkTree().getAbsolutePath())
          .append("\"\n");
      message.append("\t- WORKING_DIR: \"").append(this.workingDir.getAbsolutePath()).append("\"\n");
      message.append("\t- WORKING_DIR_PARENT_TO_WORKTREE: \"").append(this.workingDirParentToGitWorkTree)
          .append("\"\n");
      this.log.fine(message.toString());
    }
  }

  @Override
  public void close() {
    if (this.git != null) {
      this.git.close();
    }
  }

  public void testConnection(String repositoryUrl) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(ScmProviderGit.LOG_PREFIX + "Testing repository connection (URL: " + repositoryUrl + ").");
    }

    try {
      LsRemoteCommand lsRemote = Git.lsRemoteRepository().setHeads(true).setRemote(repositoryUrl);
      setAuthenticationDetails(lsRemote);

      Collection<Ref> result = lsRemote.call();
      if (result.isEmpty()) {
        throw new ScmException(ScmOperation.INFO, "No connection could be established to repository: " + repositoryUrl);
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO, "No connection could be established to repository: " + repositoryUrl,
          e);
    }
  }

  @Override
  public void checkout(CheckoutRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Checking out from remote repository.");
    }

    // check if local working dir is empty
    if (this.workingDir.exists() && this.workingDir.list().length > 0) {
      throw new ScmException(ScmOperation.CHECKOUT,
          "Unable to checkout remote repository '" + request.getRemoteRepositoryUrl() + "'. Local working directory '"
              + this.workingDir.getAbsolutePath() + "' is not empty!");
    }

    try {
      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Cloning remote repository.");
        StringBuilder message = new StringBuilder(LOG_PREFIX).append("Clone info:\n");
        message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
        message.append("\t- REMOTE_URL: ").append(request.getRemoteRepositoryUrl());
        this.log.fine(message.toString());
      }

      CloneCommand clone = this.gitCommandFactory.getCloneCommand().setDirectory(this.workingDir)
          .setURI(request.getRemoteRepositoryUrl());
      setAuthenticationDetails(clone);
      if (!request.checkoutWholeRepository()) {
        clone.setNoCheckout(true);
      }
      this.git = clone.call();
      this.util = new GitUtil(this.git);

      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Cloning remote repository finished successfully.\n");
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.CHECKOUT,
          "Unable to clone remote git repository '" + request.getRemoteRepositoryUrl()
              + "' into local working directory '" + this.workingDir.getAbsolutePath() + "'.",
          e);
    }

    if (!request.checkoutWholeRepository()) {
      // 1. checkout single filepaths from the repository
      String revision = request.getRevision().or(Constants.HEAD);
      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Checking out single files only.");
        StringBuilder message = new StringBuilder(LOG_PREFIX).append("Checkout info:\n");
        message.append("\t- FILES: ").append(Joiner.on(',').join(request.getPathsToCheckout())).append('\n');
        message.append("\t- REVISION: ").append(revision);
        this.log.fine(message.toString());
      }

      try {
        CheckoutCommand checkout = this.gitCommandFactory.getCheckoutCommand(this.git, request).setStartPoint(revision)
            .setAllPaths(false);
        for (String path : request.getPathsToCheckout()) {
          checkout.addPath(path);
        }
        checkout.call();
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.CHECKOUT,
            "Unable to checkout commit with id '" + request.getRevision().get() + "' into local working directory '"
                + this.workingDir.getAbsolutePath() + "'.",
            e);
      }
    } else if (request.checkoutBranch()) {
      // 2. checkout a specific branch (and even a commit from this branch)
      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Checking out branch" + request.getBranch().get() + ".");
      }

      if (hasBranch(request.getBranch().get())) {
        RevCommit startPoint = this.util.resolveCommit(request.getRevision(), request.getBranch());
        if (this.log.isLoggable(Level.FINE)) {
          StringBuilder message = new StringBuilder(LOG_PREFIX).append("Checkout info:\n");
          message.append("\t- BRANCH: ").append(request.getBranch().get()).append('\n');
          message.append("\t- REVISION: ").append(startPoint.getName());
          this.log.fine(message.toString());
        }

        try {
          CheckoutCommand checkout = this.gitCommandFactory.getCheckoutCommand(this.git, request)
              .setName(request.getBranch().get()).setCreateBranch(true).setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
              .setStartPoint(startPoint);
          checkout.call();
        } catch (GitAPIException e) {
          throw new ScmException(ScmOperation.CHECKOUT, "Unable to checkout '" + request.getBranch().get()
              + "' into local working directory '" + this.workingDir.getAbsolutePath() + "'.", e);
        }
      } else {
        if (this.log.isLoggable(Level.WARNING)) {
          StringBuilder message = new StringBuilder(LOG_PREFIX)
              .append("The remote repository contains no branch with name '").append(request.getBranch().get())
              .append("'. Staying on current branch '").append(this.util.getCurrentBranchName()).append("'.");
          this.log.warning(message.toString());
        }
      }
    } else if (request.checkoutTag()) {
      // 3. checkout a specific tag
      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Checking out tag" + request.getTag().get() + ".");
      }
      if (hasTag(request.getTag().get())) {
        try {
          CheckoutCommand checkout = this.gitCommandFactory.getCheckoutCommand(this.git, request)
              .setName(request.getTag().get());
          checkout.call();
        } catch (GitAPIException e) {
          throw new ScmException(ScmOperation.CHECKOUT, "Unable to checkout tag '" + request.getTag().get()
              + "' into local working directory '" + this.workingDir.getAbsolutePath() + "'.", e);
        }
      } else {
        if (this.log.isLoggable(Level.WARNING)) {
          StringBuilder message = new StringBuilder(LOG_PREFIX)
              .append("The remote repository contains no tag with name '").append(request.getTag().get())
              .append("'. Staying on current branch '").append(this.util.getCurrentBranchName()).append("'.");
          this.log.warning(message.toString());
        }
      }
    } else if (request.getRevision().isPresent()) {
      // 4. checkout a specific commit if no branch or tag is specified
      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Checking out a specific revision from current branch.");
        StringBuilder message = new StringBuilder(LOG_PREFIX).append("Checkout info:\n");
        message.append("\t- BRANCH: ").append(this.util.getCurrentBranchName()).append('\n');
        message.append("\t- REVISION: ").append(request.getRevision().get());
        this.log.fine(message.toString());
      }
      try {
        CheckoutCommand checkout = this.gitCommandFactory.getCheckoutCommand(this.git, request)
            .setName(request.getRevision().get());
        checkout.call();
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.CHECKOUT,
            "Unable to checkout commit with id '" + request.getRevision().get() + "' into local working directory '"
                + this.workingDir.getAbsolutePath() + "'.",
            e);
      }
    }

    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Checkout finished successfully!");
    }
  }

  @Override
  public String commit(CommitRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Committing local changes.");
    }

    final Set<String> pathsToCommitRelativizedToWorkTree = relativizePathsToWorkTree(request.getPathsToCommit());
    if (!this.util.isDirty(pathsToCommitRelativizedToWorkTree)) {
      if (this.log.isLoggable(Level.INFO)) {
        this.log.info(LOG_PREFIX + "Nothing to commit here.");
      }
      return request.push() ? getLatestRemoteRevision() : getLocalRevision();
    }

    final String logMessage = StringUtils.trim(request.getMessage());

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX + "Commit info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy()).append('\n');
      message.append("\t- PUSH: ").append(request.push()).append('\n');
      message.append("\t- COMMIT_ALL_CHANGES: ").append(request.commitAllChanges());
      if (!request.commitAllChanges()) {
        message.append("\n\t- FILES: ").append(Joiner.on(',').join(pathsToCommitRelativizedToWorkTree));
      }
      message.append("\n\t- LOG MESSAGE: [").append(logMessage).append("]");
      this.log.fine(message.toString());
    }

    // add all changes to be committed (either everything or the specified paths)
    AddCommand add = this.gitCommandFactory.getAddCommand(this.git);
    if (request.commitAllChanges()) {
      if (request.includeUntrackedFiles()) {
        add.addFilepattern(".");
      } else {
        for (String path : this.util.getUncommittedChangedPaths()) {
          add.addFilepattern(path);
        }
      }
    } else {
      for (String path : pathsToCommitRelativizedToWorkTree) {
        add.addFilepattern(path);
      }
    }
    try {
      add.call();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.COMMIT, "Unable to add local changes to the index.", e);
    }

    // commit all added changes
    CommitCommand commit = this.gitCommandFactory.getCommitCommand(this.git, request).setMessage(logMessage)
        .setCommitter(this.personIdent);
    if (request.commitAllChanges()) {
      commit.setAll(true);
    } else {
      for (String path : pathsToCommitRelativizedToWorkTree) {
        commit.setOnly(path);
      }
    }

    String newRevision = null;
    try {
      RevCommit result = commit.call();
      newRevision = result.getName();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.DELETE_TAG, "Could not commit changes of local repository.", e);
    }

    if (request.push()) {
      PushRequest pr = PushRequest.builder().mergeStrategy(request.getMergeStrategy())
          .mergeClient(request.getMergeClient().orNull()).build();
      push(pr);
      newRevision = getLatestRemoteRevision();
    }

    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Commit finished successfully. New revision is: " + newRevision);
    }

    return newRevision;
  }

  private Set<String> relativizePathsToWorkTree(Set<String> pathsRelativeToWorkingDir) {
    final Set<String> ret = Sets.newHashSet();
    for (String pathRelativeToWorkingDir : pathsRelativeToWorkingDir) {
      ret.add(this.workingDirParentToGitWorkTree + pathRelativeToWorkingDir);
    }
    return ret;
  }

  @Override
  public String push(PushRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Pushing local changes to remote repository.");
    }

    String localBranchName = this.util.getCurrentBranchName();
    String remoteBranchName = this.util.getRemoteBranchName(localBranchName);
    String remoteName = this.util.getRemoteName(localBranchName);
    String remoteUrl = this.util.getConnectionUrlOfRemote(remoteName);

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX + "Push info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- LOCAL_BRANCH: ").append(localBranchName).append('\n');
      message.append("\t- REMOTE_BRANCH: ").append(remoteBranchName).append('\n');
      message.append("\t- REMOTE: ").append(remoteName).append('\n');
      message.append("\t- REMOTE_URL: ").append(remoteUrl).append('\n');
      message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy()).append('\n');
      this.log.fine(message.toString());
    }

    // 1. update the local repository before pushing to remote
    UpdateRequest ur = UpdateRequest.builder().mergeStrategy(request.getMergeStrategy())
        .mergeClient(request.getMergeClient().orNull()).build();
    update(ur);

    // 2. push local changes to remote repository
    PushCommand push = this.gitCommandFactory.getPushCommand(this.git).setRemote(remoteName).setPushAll().setPushTags();
    for (String additional : this.additionalThingsToPush) {
      push.add(additional);
    }
    callPush(push, ScmOperation.PUSH, "Could not push local commits to the remote repository");
    this.additionalThingsToPush.clear();

    String newRemoteRevision = getLatestRemoteRevision();
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Push finished successfully. New remote revision is: " + newRemoteRevision);
    }
    return newRemoteRevision;
  }

  /**
   * @param push
   * @param scmOperation
   * @param baseErrorMessage
   */
  private void callPush(final PushCommand push, final ScmOperation scmOperation, final String baseErrorMessage) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    push.setOutputStream(out);
    try {
      setAuthenticationDetails(push);
      final Iterable<PushResult> results = push.call();

      Status failureStatus = null;
      String reason = null;
      resultLoop: for (PushResult result : results) {
        Collection<RemoteRefUpdate> updates = result.getRemoteUpdates();
        for (RemoteRefUpdate update : updates) {
          if (update.getStatus() != Status.OK && update.getStatus() != Status.UP_TO_DATE) {
            failureStatus = update.getStatus();
            reason = update.getMessage();
            break resultLoop;
          }
        }
      }
      final String remoteOutput = out.toString();
      if (failureStatus != null) {
        StringBuilder message = new StringBuilder(baseErrorMessage).append(" due to the following error: [")
            .append(failureStatus).append("] ");
        if (reason != null) {
          message.append(reason);
        }
        if (StringUtils.isNotBlank(remoteOutput)) {
          message.append("\nRemote details:\n").append(remoteOutput);
        } else {
          message.append("\nNo further details provided by the remote Git server.");
        }
        throw new ScmException(scmOperation, message.toString());
      }
      if (StringUtils.isNotBlank(remoteOutput) && this.log.isLoggable(Level.INFO)) {
        for (String line : StringUtils.split(remoteOutput, StringUtils.LF)) {
          this.log.info(LOG_PREFIX + "remote: " + StringUtils.remove(line, StringUtils.CR));
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(scmOperation, baseErrorMessage, e);
    }
  }

  @Override
  public String update(UpdateRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Updating local repository with remote changes.");
    }

    String localBranchName = this.util.getCurrentBranchName();
    String remoteBranchName = this.util.getRemoteBranchName(localBranchName);
    String remoteName = this.util.getRemoteName(localBranchName);
    String connectionUrl = this.util.getConnectionUrlOfRemote(remoteName);
    // TODO update paths only?

    if (this.log.isLoggable(Level.FINE)) {
      this.log.fine(LOG_PREFIX + "Fetching remote updates.");
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Fetch info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- REMOTE: ").append(remoteName).append('\n');
      message.append("\t- REMOTE_URL: ").append(connectionUrl);
      this.log.fine(message.toString());
    }

    try {
      FetchCommand fetch = this.gitCommandFactory.getFetchCommand(this.git).setRemote(remoteName)
          .setTagOpt(TagOpt.AUTO_FOLLOW).setRemoveDeletedRefs(true);
      setAuthenticationDetails(fetch);
      fetch.call();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.UPDATE,
          "Could not fetch changes from Git remote '" + remoteName + " [" + connectionUrl + "]'.", e);
    }

    MergeCommand merge = this.gitCommandFactory.getMergeCommand(this.git).setFastForward(FastForwardMode.FF)
        .setCommit(true).setMessage("Merge");
    switch (request.getMergeStrategy()) {
      case USE_LOCAL:
        merge.setStrategy(MergeStrategy.OURS);
        break;
      case USE_REMOTE:
        merge.setStrategy(MergeStrategy.THEIRS);
        break;
      case FULL_MERGE:
        merge.setStrategy(new UnleashGitFullMergeStrategy(request.getMergeClient().get()));
        break;
      case DO_NOT_MERGE:
        // nothing to do here!
        break;
      default:
        throw new UnsupportedOperationException(
            "Unknown merge strategy! API and implementation versions are incompatible!");
    }

    String requestedRevision = request.getTargetRevision().or(getLatestRemoteRevision());
    try {
      ObjectId revision = this.git.getRepository().resolve(requestedRevision);
      merge.include(revision);
    } catch (Exception e) {
      throw new ScmException(ScmOperation.MERGE, "No Git commit id found for String '" + requestedRevision + "'.", e);
    }

    if (this.log.isLoggable(Level.FINE)) {
      this.log.fine(LOG_PREFIX + "Merging remote updates into local working copy.");
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Merge info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- REMOTE: ").append(remoteName).append('\n');
      message.append("\t- REMOTE_BRANCH: ").append(remoteBranchName).append('\n');
      message.append("\t- REMOTE_REVISION: ").append(requestedRevision).append('\n');
      message.append("\t- LOCAL_BRANCH: ").append(localBranchName).append('\n');
      message.append("\t- LOCAL_REVISION: ").append(getLocalRevision()).append('\n');
      message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy());
      this.log.fine(message.toString());
    }

    try {
      merge.call();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.MERGE, "Could not merge changes fetched from Git remote '" + remoteName + " ["
          + connectionUrl + "]' into local working copy '" + this.workingDir.getAbsolutePath() + "'.", e);
    }

    String newRevision = getLocalRevision();
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Update finished successfully. New revision is: " + newRevision);
    }
    return newRevision;
  }

  @Override
  public String tag(TagRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Tagging local repository with '" + request.getTagName() + "'");
    }

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Tag info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- TAG_NAME: ").append(request.getTagName()).append('\n');
      message.append("\t- PRE_TAG_COMMIT_MESSAGE: ").append(request.getPreTagCommitMessage()).append('\n');
      message.append("\t- SCM_MESSAGE_PREFIX: ").append(request.getScmMessagePrefix()).append('\n');
      message.append("\t- USE_WORKING_COPY: ").append(request.tagFromWorkingCopy()).append('\n');
      if (request.tagFromWorkingCopy()) {
        message.append("\t- COMMIT_BEFORE_TAGGING: ").append(request.commitBeforeTagging()).append('\n');
        message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy());
      } else {
        message.append("\t- REMOTE_URL: ").append(request.getRemoteRepositoryUrl()).append('\n');
        message.append("\t- REVISION: ").append(request.getRevision());
      }
      this.log.fine(message.toString());
    }

    if (request.tagFromWorkingCopy()) {
      // 1. commit the changes (no merging because we stay local!)
      final StringBuilder defaultPreTagCommitMessage = new StringBuilder(
          "Preparation for tag creation (Tag name: '" + request.getTagName() + "').");
      // prepend scmMessagePrefix if needed
      if (request.getScmMessagePrefix().isPresent()) {
        defaultPreTagCommitMessage.insert(0, request.getScmMessagePrefix().get());
      }
      String preTagCommitMessage = request.getPreTagCommitMessage().or(defaultPreTagCommitMessage.toString());
      Builder builder = CommitRequest.builder().message(preTagCommitMessage);
      if (request.includeUntrackedFiles()) {
        builder.includeUntrackedFiles();
      }
      commit(builder.build());

      try {
        // 2. tag local revision
        TagCommand tag = this.gitCommandFactory.getTagCommand(this.git, request).setName(request.getTagName())
            .setMessage(request.getMessage()).setAnnotated(true).setTagger(this.personIdent);
        tag.call();
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.TAG, "An error occurred during local Git tag creation.", e);
      }

      if (!request.commitBeforeTagging()) {
        try {
          // 3. deletes the local commit that had been done for tag creation.
          this.gitCommandFactory.getResetCommand(this.git).setMode(ResetType.MIXED).setRef(Constants.HEAD + "~1")
              .call();
        } catch (GitAPIException e) {
          throw new ScmException(ScmOperation.TAG,
              "An error occurred during local commit resetting (no pre-tag commit was requested).", e);
        }
      }

      String newRevision;
      String tagPushName = GitUtil.TAG_NAME_PREFIX + request.getTagName();
      if (request.push()) {
        if (request.commitBeforeTagging()) {
          // if the commit shall be kept, push everything with update of the local WC!
          PushRequest pr = PushRequest.builder().mergeStrategy(request.getMergeStrategy())
              .mergeClient(request.getMergeClient().orNull()).build();
          newRevision = push(pr);
        } else {
          // if the commit was deleted, just push the tag
          String localBranchName = this.util.getCurrentBranchName();
          String remoteName = this.util.getRemoteName(localBranchName);
          String connectionUrl = this.util.getConnectionUrlOfRemote(remoteName);
          PushCommand push = this.gitCommandFactory.getPushCommand(this.git).setRemote(remoteName).add(tagPushName);
          callPush(push, ScmOperation.PUSH, "Unable to push locally created tag '" + request.getTagName()
              + "' to remote '" + remoteName + "[" + connectionUrl + "]'");
          newRevision = getLatestRemoteRevision();
        }
      } else {
        newRevision = getLocalRevision();
      }

      if (this.log.isLoggable(Level.INFO)) {
        this.log.info(LOG_PREFIX + "Tag creation finished successfully. New revision is: " + newRevision);
      }
      return newRevision;
    } else {
      // TODO implement tagging from url!
      // git doesn't support remote tagging but we could clone in bare mode, create the tag, push it and delete the
      // cloned repo afterwards
      throw new UnsupportedOperationException(
          "This SCM provider doesn't support tagging from remote URLs only. This feature needs some workarounds and is scheduled for a later version.");
    }
  }

  @Override
  public boolean hasTag(String tagName) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Searching for Git tag '" + tagName + "'");
    }

    String localBranchName = this.util.getCurrentBranchName();
    String remoteName = this.util.getRemoteName(localBranchName);
    String remoteUrl = this.util.getConnectionUrlOfRemote(remoteName);

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Query info:\n");
      message.append("\t- TAG_NAME: ").append(tagName).append('\n');
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- REMOTE: ").append(remoteName).append('\n');
      message.append("\t- REMOTE_URL: ").append(remoteUrl);
      this.log.fine(message.toString());
    }

    try {
      LsRemoteCommand lsRemote = this.gitCommandFactory.getLsRemoteCommand(this.git).setRemote(remoteName)
          .setTags(true);
      setAuthenticationDetails(lsRemote);

      String tagRefName = GitUtil.TAG_NAME_PREFIX + tagName;
      for (Ref tag : lsRemote.call()) {
        if (Objects.equal(tag.getName(), tagRefName)) {
          return true;
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while querying the remote git repository for tag '" + tagName + "'.", e);
    }
    return false;
  }

  @Override
  public String deleteTag(DeleteTagRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Deleting Git tag");
    }

    String localBranchName = this.util.getCurrentBranchName();
    String remoteName = this.util.getRemoteName(localBranchName);
    String remoteUrl = this.util.getConnectionUrlOfRemote(remoteName);

    boolean hasRemoteTag = hasTag(request.getTagName());
    // 1. fetch the tag from remote if it is not known locally and the remote one exists
    if (!this.util.hasLocalTag(request.getTagName()) && hasRemoteTag) {
      if (this.log.isLoggable(Level.FINE)) {
        this.log.fine(LOG_PREFIX + "Fetching remote tag");
      }
      try {
        FetchCommand fetch = this.gitCommandFactory.getFetchCommand(this.git).setRemote(remoteName)
            .setTagOpt(TagOpt.FETCH_TAGS);
        setAuthenticationDetails(fetch);
        fetch.call();
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.DELETE_TAG, "Unable to fetch tags for deletion of tag '"
            + request.getTagName() + "' from remote '" + remoteName + "[" + remoteUrl + "]'.", e);
      }
    }

    // proceed only if tag exists locally (if this is not the case at this point, it doesn't exist remotely either)
    if (this.util.hasLocalTag(request.getTagName())) {
      if (this.log.isLoggable(Level.FINE)) {
        StringBuilder message = new StringBuilder(LOG_PREFIX).append("Tag info:\n");
        message.append("\t- TAG_NAME: ").append(request.getTagName()).append('\n');
        message.append("\t- REMOTE: ").append(remoteName).append('\n');
        message.append("\t- REMOTE_URL: ").append(remoteUrl);
        this.log.fine(message.toString());
      }

      try {
        // 2. delete the tag locally
        DeleteTagCommand deleteTag = this.gitCommandFactory.getDeleteTagCommand(this.git, request)
            .setTags(GitUtil.TAG_NAME_PREFIX + request.getTagName());
        deleteTag.call();
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.DELETE_TAG,
            "An error occurred during the local deletion of tag '" + request.getTagName() + "'.", e);
      }

      // 3. if the tag exists in the remote repository the remote tag gets either deleted or will be scheduled for
      // deletion on next push
      if (hasRemoteTag) {
        String tagPushName = ":" + GitUtil.TAG_NAME_PREFIX + request.getTagName();
        if (request.push()) {
          PushCommand push = this.gitCommandFactory.getPushCommand(this.git).setRemote(remoteName).add(tagPushName);
          callPush(push, ScmOperation.DELETE_TAG, "An error occurred during the deletion of tag '"
              + request.getTagName() + "' from remote '" + remoteName + "[" + remoteUrl + "]'");
        } else {
          this.additionalThingsToPush.add(tagPushName);
        }
      }
    }

    return getLatestRemoteRevision();
  }

  @Override
  public String branch(BranchRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Branching local repository.");
    }

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Branch info:\n");
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- BRANCH_NAME: ").append(request.getBranchName()).append('\n');
      message.append("\t- USE_WORKING_COPY: ").append(request.branchFromWorkingCopy()).append('\n');
      if (request.branchFromWorkingCopy()) {
        message.append("\t- COMMIT_BEFORE_BRANCHING: ").append(request.commitBeforeBranching()).append('\n');
        message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy());
      } else {
        message.append("\t- REMOTE_URL: ").append(request.getRemoteRepositoryUrl()).append('\n');
        message.append("\t- REVISION: ").append(request.getRevision());
      }
      this.log.fine(message.toString());
    }

    if (request.branchFromWorkingCopy()) {
      if (this.util.hasLocalBranch(request.getBranchName())) {
        // QUESTION eventually checkout local branch instead?
        throw new ScmException(ScmOperation.BRANCH, "A local branch with this name already exists!");
      }

      if (hasBranch(request.getBranchName())) {
        // QUESTION eventually fetch branch and create a local one tracking this one instead?
        throw new ScmException(ScmOperation.BRANCH, "A remote branch with this name already exists!");
      }

      // 1. commit the changes if branching from WC is requested(no merging!)
      if (!request.getRevision().isPresent()) {
        String preBranchCommitMessage = request.getPreBranchCommitMessage() != null
            ? request.getPreBranchCommitMessage()
            : request.getMessage();
        CommitRequest cr = CommitRequest.builder().message(preBranchCommitMessage).noMerge().build();
        commit(cr);
      }

      try {
        // 2. branch from WC
        CreateBranchCommand branch = this.gitCommandFactory.getCreateBranchCommand(this.git, request)
            .setName(request.getBranchName()).setUpstreamMode(SetupUpstreamMode.TRACK)
            .setStartPoint(request.getRevision().or(Constants.HEAD));
        branch.call();
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.BRANCH, "Could not create local branch '" + request.getBranchName()
            + "' in working copy '" + this.workingDir.getAbsolutePath() + "'", e);
      }

      // 3. deletes the local commit that had been done for branch creation.
      if (!request.commitBeforeBranching()) {
        try {
          this.gitCommandFactory.getResetCommand(this.git).setMode(ResetType.MIXED).setRef(Constants.HEAD + "~1")
              .call();
        } catch (GitAPIException e) {
          throw new ScmException(ScmOperation.BRANCH,
              "An error occurred during local commit resetting (no pre-branch commit was requested).", e);
        }
      }

      String branchPushName = GitUtil.HEADS_NAME_PREFIX + request.getBranchName();
      String newRevision;
      if (request.push()) {
        if (request.commitBeforeBranching()) {
          // if the commit shall be kept, push everything with update of the local WC!
          PushRequest pr = PushRequest.builder().mergeStrategy(request.getMergeStrategy())
              .mergeClient(request.getMergeClient().orNull()).build();
          newRevision = push(pr);
        } else {
          String localBranchName = this.util.getCurrentBranchName();
          String remoteName = this.util.getRemoteName(localBranchName);
          String connectionUrl = this.util.getConnectionUrlOfRemote(remoteName);
          PushCommand push = this.gitCommandFactory.getPushCommand(this.git).setRemote(remoteName).add(branchPushName);
          callPush(push, ScmOperation.PUSH, "Unable to push locally created branch '" + request.getBranchName()
              + "' to remote '" + remoteName + "[" + connectionUrl + "]'");
          newRevision = getLatestRemoteRevision();
        }
      } else {
        newRevision = getLocalRevision();
      }

      if (this.log.isLoggable(Level.INFO)) {
        this.log.info(LOG_PREFIX + "Branch creation finished successfully. New revision is: " + newRevision);
      }
      return newRevision;
    } else {
      // TODO implement remote branching -> similar to remote tagging!
      throw new UnsupportedOperationException(
          "This SCM provider doesn't support tagging from remote URLs only. This feature needs some workarounds and is scheduled for a later version.");
    }
  }

  @Override
  public boolean hasBranch(String branchName) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Searching for Git branch");
    }

    String localBranchName = this.util.getCurrentBranchName();
    String remoteName = this.util.getRemoteName(localBranchName);
    String remoteUrl = this.util.getConnectionUrlOfRemote(remoteName);

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Query info:\n");
      message.append("\t- BRANCH_NAME: ").append(branchName).append('\n');
      message.append("\t- WORKING_DIR: ").append(this.workingDir.getAbsolutePath()).append('\n');
      message.append("\t- REMOTE: ").append(remoteName).append('\n');
      message.append("\t- REMOTE_URL: ").append(remoteUrl);
      this.log.fine(message.toString());
    }

    try {
      LsRemoteCommand lsRemote = this.gitCommandFactory.getLsRemoteCommand(this.git).setRemote(remoteName)
          .setHeads(true);
      setAuthenticationDetails(lsRemote);
      Collection<Ref> branches = lsRemote.call();
      for (Ref branch : branches) {
        if (Objects.equal(branch.getName(), GitUtil.HEADS_NAME_PREFIX + branchName)) {
          return true;
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while querying the remote git repository for branch '" + branchName + "'.", e);
    }
    return false;
  }

  @Override
  public String deleteBranch(DeleteBranchRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Deleting Git branch");
    }

    String localBranchName = this.util.getCurrentBranchName();
    String remoteName = this.util.getRemoteName(localBranchName);
    String remoteUrl = this.util.getConnectionUrlOfRemote(remoteName);

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Branch info:\n");
      message.append("\t- TAG_NAME: ").append(request.getBranchName()).append('\n');
      message.append("\t- REMOTE: ").append(remoteName).append('\n');
      message.append("\t- REMOTE_URL: ").append(remoteUrl);
      this.log.fine(message.toString());
    }

    if (this.util.hasLocalBranch(request.getBranchName())) {
      try {
        this.gitCommandFactory.getDeleteBranchCommand(this.git, request)
            .setBranchNames(GitUtil.HEADS_NAME_PREFIX + request.getBranchName()).setForce(true).call();
      } catch (GitAPIException e) {
        e.printStackTrace();
      }
    }

    if (hasBranch(request.getBranchName())) {
      if (request.push()) {
        PushCommand push = this.gitCommandFactory.getPushCommand(this.git).setRemote(remoteName)
            .add(":" + GitUtil.HEADS_NAME_PREFIX + request.getBranchName());
        callPush(push, ScmOperation.DELETE_BRANCH, "An error occurred during the deletion of branch '"
            + request.getBranchName() + "' from remote '" + remoteName + "[" + remoteUrl + "]'");
      } else {
        this.additionalThingsToPush.add(":" + GitUtil.HEADS_NAME_PREFIX + request.getBranchName());
      }
    }

    return getLatestRemoteRevision();
  }

  @Override
  public String revertCommits(RevertCommitsRequest request) throws ScmException {
    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Reverting Git commits");
    }

    if (this.log.isLoggable(Level.FINE)) {
      StringBuilder message = new StringBuilder(LOG_PREFIX).append("Commit info:\n");
      message.append("\t- FROM: ").append(request.getFromRevision()).append('\n');
      message.append("\t- TO: ").append(request.getToRevision()).append('\n');
      message.append("\t- MERGE_STRATEGY: ").append(request.getMergeStrategy()).append('\n');
      this.log.fine(message.toString());
    }

    // update to HEAD revision first then revert commits!
    UpdateRequest updateRequest = UpdateRequest.builder().mergeStrategy(request.getMergeStrategy())
        .mergeClient(request.getMergeClient().orNull()).build();
    update(updateRequest);

    RevCommit from = this.util.resolveCommit(Optional.of(request.getFromRevision()), Optional.<String> absent());
    RevCommit to = this.util.resolveCommit(Optional.of(request.getToRevision()), Optional.<String> absent());
    int diff = from.getCommitTime() - to.getCommitTime();
    if (diff == 0) {
      // nothing to revert! return the latest remote version
      return getLatestRemoteRevision();
    } else if (diff < 0) {
      // older from version (wrong direction!
      throw new ScmException(ScmOperation.REVERT_COMMITS,
          "Error reverting commits in remote repository. \"FROM\" revision (" + request.getFromRevision()
              + ") is older than \"TO\" revision (" + request.getToRevision() + ")");
    }

    try {
      RevertCommand revert = this.gitCommandFactory.getRevertCommand(this.git, request);

      List<RevCommit> commitsToRevert = this.util.resolveCommitRange(request.getToRevision(),
          request.getFromRevision());
      for (RevCommit commit : commitsToRevert) {
        revert.include(commit);
      }

      switch (request.getMergeStrategy()) {
        case USE_LOCAL:
          revert.setStrategy(MergeStrategy.OURS);
          break;
        case USE_REMOTE:
          revert.setStrategy(MergeStrategy.THEIRS);
          break;
        case FULL_MERGE:
          revert.setStrategy(new UnleashGitFullMergeStrategy(request.getMergeClient().get()));
          break;
        case DO_NOT_MERGE:
          // nothing to do here!
          break;
        default:
          throw new UnsupportedOperationException(
              "Unknown merge strategy! API and implementation versions are incompatible!");
      }
      revert.call();
    } catch (Exception e) {
      throw new ScmException(ScmOperation.REVERT_COMMITS, "An error occurred during the reversion of commits.", e);
    }

    String newRevision;
    if (request.push()) {
      PushRequest pr = PushRequest.builder().mergeStrategy(request.getMergeStrategy())
          .mergeClient(request.getMergeClient().orNull()).build();
      newRevision = push(pr);
    } else {
      newRevision = getLocalRevision();
    }

    if (this.log.isLoggable(Level.INFO)) {
      this.log.info(LOG_PREFIX + "Revert finished successfully. New revision is: " + newRevision);
    }

    return newRevision;
  }

  @Override
  public String getLocalRevision() {
    try {
      RevCommit revCommit = this.gitCommandFactory.getLogCommand(this.git).call().iterator().next();
      return revCommit.getName();
    } catch (GitAPIException e) {
      throw new IllegalStateException("Could not determine the last revision commit of the local repository.", e);
    }
  }

  @Override
  public String getLatestRemoteRevision() {
    try {
      String localBranchName = this.util.getCurrentBranchName();
      String remoteName = this.util.getRemoteName(localBranchName);
      String remoteNameBranch = this.util.getRemoteBranchName(localBranchName);

      LsRemoteCommand lsRemote = this.gitCommandFactory.getLsRemoteCommand(this.git).setHeads(true)
          .setRemote(remoteName);
      setAuthenticationDetails(lsRemote);
      Collection<Ref> branches = lsRemote.call();
      for (Ref branch : branches) {
        if (Objects.equal(branch.getTarget().getName(), remoteNameBranch)) {
          return branch.getObjectId().getName();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";
  }

  @Override
  public String calculateTagConnectionString(String currentConnectionString, String tagName) {
    // connection string only points to the git dir and branches/tags have to be specified and checked out separately
    return currentConnectionString;
  }

  @Override
  public String calculateBranchConnectionString(String currentConnectionString, String branchName) {
    // connection string only points to the git dir and branches/tags have to be specified and checked out separately
    return currentConnectionString;
  }

  @Override
  public boolean isTagInfoIncludedInConnection() {
    // connection string only points to the git dir and branches/tags have to be specified and checked out separately
    return false;
  }

  @Override
  // TODO logging!
  public HistoryResult getHistory(HistoryRequest request) throws ScmException {
    HistoryResult.Builder historyBuilder = HistoryResult.builder();

    if (request.getRemoteRepositoryUrl().isPresent()) {
      throw new ScmException(ScmOperation.INFO, "Remote history retrieval is not supported by Git!");
    }

    try {
      LogCommand logCommand = this.gitCommandFactory.getLogCommand(this.git);
      if (request.getMessageFilters().isEmpty()) {
        // set the limit of commits to be retrieved only if no filters are provided since the user wants to see the
        // specified number of commits but some could be filtered out
        logCommand.setMaxCount((int) request.getMaxResults());
      }

      for (String path : request.getPathFilters()) {
        logCommand.addPath(path);
      }

      AnyObjectId startId = getTagRevisionOrDefault(request.getStartTag(), request.getStartRevision().orNull());
      AnyObjectId endId = getTagRevisionOrDefault(request.getEndTag(),
          request.getEndRevision().or(this.git.getRepository().resolve("HEAD").name()));

      if (startId != null && endId != null) {
        logCommand.addRange(startId, endId);
      } else if (startId != null) {
        logCommand.add(startId);
      } else if (endId != null) {
        logCommand.add(endId);
      }

      Set<String> messageFilterPatterns = request.getMessageFilters();
      int commitsAdded = 0;
      for (RevCommit revCommit : logCommand.call()) {
        if (commitsAdded == request.getMaxResults()) {
          break;
        }
        if (isFilteredMessage(revCommit.getShortMessage(), messageFilterPatterns)) {
          continue;
        }

        HistoryCommit.Builder b = HistoryCommit.builder();
        b.setRevision(revCommit.getId().name());
        b.setMessage(revCommit.getShortMessage());
        PersonIdent authorIdent = revCommit.getAuthorIdent();
        b.setAuthor(authorIdent.getName());
        b.setDate(authorIdent.getWhen());
        historyBuilder.addCommit(b.build());
        commitsAdded++;
      }
    } catch (Exception e) {
      throw new ScmException(ScmOperation.INFO, "Unable to retrieve the Git log history.", e);
    }

    return historyBuilder.build();
  }

  private boolean isFilteredMessage(String message, Set<String> messageFilterPatterns) {
    for (String filter : messageFilterPatterns) {
      if (message.matches(filter)) {
        return true;
      }
    }
    return false;
  }

  private AnyObjectId getTagRevisionOrDefault(Optional<String> tag, String defaultRevision) {
    if (tag.isPresent()) {
      try {
        String tagName = GitUtil.TAG_NAME_PREFIX + tag.get();
        for (Ref ref : this.gitCommandFactory.getListTagCommand(this.git).call()) {
          if (Objects.equal(tagName, ref.getName())) {
            Ref peeledRef = this.git.getRepository().getRefDatabase().peel(ref);
            return MoreObjects.firstNonNull(peeledRef.getPeeledObjectId(), peeledRef.getObjectId());
          }
        }
        throw new ScmException(ScmOperation.INFO, "Could not find a tag with name " + tag.get());
      } catch (Exception e) {
        throw new ScmException(ScmOperation.INFO, "Unable to get the revision of the following tag: " + tag.get(), e);
      }
    }
    return defaultRevision != null ? ObjectId.fromString(defaultRevision) : null;
  }

  @Override
  public DiffResult getDiff(DiffRequest request) throws ScmException {
    if (request.getSourceRemoteRepositoryUrl().isPresent() || request.getTargetRemoteRepositoryUrl().isPresent()) {
      throw new ScmException(ScmOperation.DIFF,
          "Diff creation has been requested from remote repositories. This feature is currently not supported by the Git SCM provider.");
    }

    String sourceRevision = request.getSourceRevision().or("HEAD");
    ObjectId sourceId = null;
    try {
      sourceId = this.git.getRepository().resolve(sourceRevision);
    } catch (Exception e) {
      throw new ScmException(ScmOperation.DIFF,
          "Unable to resolve source object id using the current repository: " + sourceRevision, e);
    }

    String targetRevision = request.getTargetRevision().or("HEAD");
    ObjectId targetId = null;
    try {
      targetId = this.git.getRepository().resolve(targetRevision);
    } catch (Exception e) {
      throw new ScmException(ScmOperation.DIFF,
          "Unable to resolve target object id using the current repository: " + sourceRevision, e);
    }

    DiffResult.Builder resultBuilder = DiffResult.builder();

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    DiffFormatter df = new DiffFormatter(os);
    df.setRepository(this.git.getRepository());
    try {
      List<DiffEntry> entries = df.scan(sourceId, targetId);
      for (DiffEntry entry : entries) {
        DiffObject.Builder b = DiffObject.builder();
        switch (entry.getChangeType()) {
          case ADD:
            b.addition(entry.getNewPath());
            break;
          case DELETE:
            b.deletion(entry.getOldPath());
            break;
          case MODIFY:
            b.changed(entry.getOldPath());
            if (request.getType() == DiffType.CHANGES_ONLY) {
              df.format(entry);
              df.flush();
              String textualDiff = new String(os.toByteArray());
              b.addTextualDiff(textualDiff);
              os.reset();
            }
            break;
          case RENAME:
            b.moved(entry.getOldPath(), entry.getNewPath());
            break;
          case COPY:
            b.copied(entry.getOldPath(), entry.getNewPath());
            break;
        }

        if (request.getType() == DiffType.FULL) {
          df.format(entry);
          df.flush();
          String textualDiff = new String(os.toByteArray());
          b.addTextualDiff(textualDiff);
          os.reset();
        }

        resultBuilder.addDiff(b.build());
      }
    } catch (Exception e) {
      throw new ScmException(ScmOperation.DIFF, "Unable to calculate diff.", e);
    } finally {
      df.close();
      try {
        Closeables.close(os, true);
      } catch (IOException e) {
        // should never happen ;)
      }
    }

    return resultBuilder.build();
  }

  private void setAuthenticationDetails(TransportCommand<?, ?> command) {
    command.setCredentialsProvider(this.credentialsProvider);
    command.setTransportConfigCallback(new TransportConfigCallback() {
      @Override
      public void configure(Transport transport) {
        if (isSshTransport(transport)) {
          SshTransport sshTransport = (SshTransport) transport;
          sshTransport.setSshSessionFactory(ScmProviderGit.this.sshSessionFactory);
        }
      }

      private boolean isSshTransport(Transport transport) {
        return transport instanceof SshTransport;
      }
    });
  }
}
