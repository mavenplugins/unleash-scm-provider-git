package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.itemis.maven.plugins.unleash.scm.ScmException;
import com.itemis.maven.plugins.unleash.scm.ScmOperation;

public class GitUtil {
  public static final String SYSPROP_ENABLE_JGIT_BUILTIN_LFS = "scmGit.enableJGitBuiltinLFS";
  public static final String TAG_NAME_PREFIX = "refs/tags/";
  public static final String HEADS_NAME_PREFIX = "refs/heads/";

  private final Git git;
  private final Logger log;

  public GitUtil(Git git, Logger log) {
    this.git = git;
    this.log = log;
    enableJGitBuiltinLFSIfDefined(this.git.getRepository());
  }

  /**
   * Enable JGit built in LFS, if:
   * 1. {@link #SYSPROP_ENABLE_JGIT_BUILTIN_LFS} is defined true
   * or
   * 2. git config has filter.lfs.useJGitBuiltin true
   *
   * In case 1. this method will enforce local git config to set filter.lfs.useJGitBuiltin true
   *
   * @param repository
   */
  private void enableJGitBuiltinLFSIfDefined(final Repository repository) {
    if (repository == null) {
      return;
    }
    final StoredConfig gitconfig = repository.getConfig();
    boolean useJGitBuiltin = false;
    final String propVal = System.getProperty(SYSPROP_ENABLE_JGIT_BUILTIN_LFS);
    if ("".equals(propVal) || Boolean.valueOf(propVal)) {
      // Enforce to set local config
      useJGitBuiltin = true;
      gitconfig.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION, ConfigConstants.CONFIG_SECTION_LFS,
          ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, true);
      try {
        gitconfig.save();
      } catch (IOException e) {
        this.log.warning("Failed to save git config: " + e);
      }
    } else {
      useJGitBuiltin = gitconfig.getBoolean(ConfigConstants.CONFIG_FILTER_SECTION, ConfigConstants.CONFIG_SECTION_LFS,
          ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, false);
    }
    if (useJGitBuiltin) {
      BuiltinLFS.register();
    }
  }

  public boolean isDirty(Set<String> paths) throws ScmException {
    try {
      StatusCommand status = this.git.status();
      for (String path : paths) {
        status.addPath(path);
      }
      return !status.call().isClean();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO, "Could not evaluate the status of the local repository.", e);
    }
  }

  public Set<String> getUncommittedChangedPaths() throws ScmException {
    try {
      StatusCommand status = this.git.status();
      return status.call().getUncommittedChanges();
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO, "Could not evaluate the status of the local repository.", e);
    }
  }

  public String getCurrentConnectionUrl() throws ScmException {
    String localBranchName = getCurrentBranchName();
    String remoteName = getRemoteName(localBranchName);
    return getConnectionUrlOfRemote(remoteName);
  }

  public String getCurrentBranchName() throws ScmException {
    try {
      String branch = this.git.getRepository().getBranch();
      if (branch == null) {
        throw new ScmException(ScmOperation.INFO,
            "Unable to determine name of currently checked out local branch. The repository must be corrupt.");
      }
      return branch;
    } catch (IOException e) {
      throw new ScmException(ScmOperation.INFO, "Unable to determine name of currently checked out local branch.", e);
    }
  }

  public String getRemoteBranchName(String localBranch) {
    String remoteBranchName = this.git.getRepository().getConfig().getString("branch", localBranch, "merge");
    if (Strings.isNullOrEmpty(remoteBranchName)) {
      remoteBranchName = "refs/heads/" + localBranch;
    }
    return remoteBranchName;
  }

  public String getRemoteName(String localBranch) {
    String remote = this.git.getRepository().getConfig().getString("branch", localBranch, "remote");
    if (Strings.isNullOrEmpty(remote)) {
      // this can be the case if we are in detached head state or if the local git config does not contain a section for
      // the local branch which determines the remote tracking branch, ...
      try {
        List<RemoteConfig> remotes = this.git.remoteList().call();
        // TODO is there a better way to determine the name of the remote? -> maybe look into the remote and search for
        // the branch name, ...
        if (remotes.size() > 0) {
          remote = remotes.get(0).getName();
        }
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.INFO,
            "Unable to retrieve a list of remotes of the current local repository.", e);
      }
    }
    return remote;
  }

  public String getConnectionUrlOfRemote(String remoteName) {
    if (!Strings.isNullOrEmpty(remoteName)) {
      try {
        List<RemoteConfig> remotes = this.git.remoteList().call();
        for (RemoteConfig remoteConfig : remotes) {
          if (Objects.equal(remoteName, remoteConfig.getName())) {
            List<URIish> uris = remoteConfig.getURIs();
            if (uris.size() > 0) {
              return uris.get(0).toString();
            }
            return null;
          }
        }
      } catch (GitAPIException e) {
        throw new ScmException(ScmOperation.INFO,
            "Unable to retrieve a list of remotes of the current local repository.", e);
      }
    }
    return null;
  }

  public boolean hasLocalTag(String tagName) {
    try {
      List<Ref> tags = this.git.tagList().call();
      for (Ref tag : tags) {
        if (Objects.equal(tag.getName(), TAG_NAME_PREFIX + tagName)) {
          return true;
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while querying the local git repository for tag '" + tagName + "'.", e);
    }
    return false;
  }

  public boolean hasLocalBranch(String branchName) {
    try {
      List<Ref> branches = this.git.branchList().setListMode(ListMode.ALL).call();
      for (Ref branch : branches) {
        if (Objects.equal(branch.getName(), HEADS_NAME_PREFIX + branchName)) {
          return true;
        }
      }
    } catch (GitAPIException e) {
      throw new ScmException(ScmOperation.INFO,
          "An error occurred while querying the local git repository for branch '" + branchName + "'.", e);
    }
    return false;
  }

  public RevCommit resolveCommit(Optional<String> commitId, Optional<String> branchName) throws ScmException {
    try {
      LogCommand log = this.git.log();
      if (branchName.isPresent()) {
        String localBranchName = getCurrentBranchName();
        String remoteName = getRemoteName(localBranchName);
        log.add(this.git.getRepository().resolve(remoteName + "/" + branchName.get()));
      }
      if (!commitId.isPresent()) {
        log.setMaxCount(1);
      }
      Iterable<RevCommit> commits = log.call();

      if (commitId.isPresent()) {
        for (RevCommit commit : commits) {
          if (Objects.equal(commitId.get(), commit.getId().getName())) {
            return commit;
          }
        }
        throw new ScmException(ScmOperation.INFO, "Could not resolve commit with id '" + commitId.get()
            + (branchName.isPresent() ? "' for branch '" + branchName.get() + "'." : "'."));
      } else {
        return commits.iterator().next();
      }
    } catch (Exception e) {
      throw new ScmException(ScmOperation.INFO, "Could not resolve commit with id '" + commitId.or(Constants.HEAD)
          + (branchName.isPresent() ? "' for branch '" + branchName.get() + "'." : "'."), e);
    }
  }

  public List<RevCommit> resolveCommitRange(String from, String to) throws Exception {
    ObjectId fromId = this.git.getRepository().resolve(from);
    ObjectId toId = this.git.getRepository().resolve(to);
    Iterable<RevCommit> commits = this.git.log().addRange(fromId, toId).call();
    return Lists.newArrayList(commits);
  }
}
