/**
 *
 */
package com.itemis.maven.plugins.unleash.scm.providers.cdi;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.DeleteTagCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListTagCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.TagCommand;

import com.itemis.maven.plugins.unleash.scm.providers.revert.UnleashGitRevertCommand;
import com.itemis.maven.plugins.unleash.scm.requests.BranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CheckoutRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteBranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteTagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.RevertCommitsRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;

/**
 * Default implementation of {@link IGitCommandFactory}
 *
 * @author mhoffrog
 */
public class DefaultGitCommandFactory implements IGitCommandFactory {

  @Inject
  @Named("scmMessagePrefix")
  private String scmMessagePrefix;

  /**
   * Supposed to be constructed by injection only as well as to leverage inheritance.
   */
  protected DefaultGitCommandFactory() {
  }

  @Override
  public AddCommand getAddCommand(final Git git) {
    return git != null ? git.add() : null;
  }

  @Override
  public CheckoutCommand getCheckoutCommand(final Git git, final CheckoutRequest request) {
    return git != null ? git.checkout() : null;
  }

  @Override
  public CloneCommand getCloneCommand() {
    return Git.cloneRepository();
  }

  @Override
  public CommitCommand getCommitCommand(final Git git, final CommitRequest request) {
    return git != null ? git.commit() : null;
  }

  @Override
  public CreateBranchCommand getCreateBranchCommand(final Git git, final BranchRequest request) {
    return git != null ? git.branchCreate() : null;
  }

  @Override
  public DeleteBranchCommand getDeleteBranchCommand(final Git git, final DeleteBranchRequest request) {
    return git != null ? git.branchDelete() : null;
  }

  @Override
  public DeleteTagCommand getDeleteTagCommand(final Git git, DeleteTagRequest request) {
    return git != null ? git.tagDelete() : null;
  }

  @Override
  public FetchCommand getFetchCommand(final Git git) {
    return git != null ? git.fetch() : null;
  }

  @Override
  public ListTagCommand getListTagCommand(Git git) {
    return git != null ? git.tagList() : null;
  }

  @Override
  public LogCommand getLogCommand(final Git git) {
    return git != null ? git.log() : null;
  }

  @Override
  public LsRemoteCommand getLsRemoteCommand(final Git git) {
    return git != null ? git.lsRemote() : null;
  }

  @Override
  public MergeCommand getMergeCommand(final Git git) {
    return git != null ? git.merge() : null;
  }

  @Override
  public PushCommand getPushCommand(final Git git) {
    return git != null ? git.push() : null;
  }

  @Override
  public ResetCommand getResetCommand(final Git git) {
    return git != null ? git.reset() : null;
  }

  @Override
  public RevertCommand getRevertCommand(final Git git, final RevertCommitsRequest request) {
    return git != null ? new UnleashGitRevertCommand(git.getRepository(), this.scmMessagePrefix) : null;
  }

  @Override
  public TagCommand getTagCommand(final Git git, TagRequest request) {
    return git != null ? git.tag() : null;
  }

}
