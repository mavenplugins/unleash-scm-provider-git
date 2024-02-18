package com.itemis.maven.plugins.unleash.scm.providers.cdi;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.DeleteTagCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.ListTagCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.TagCommand;

import com.itemis.maven.plugins.unleash.scm.requests.BranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CheckoutRequest;
import com.itemis.maven.plugins.unleash.scm.requests.CommitRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteBranchRequest;
import com.itemis.maven.plugins.unleash.scm.requests.DeleteTagRequest;
import com.itemis.maven.plugins.unleash.scm.requests.RevertCommitsRequest;
import com.itemis.maven.plugins.unleash.scm.requests.TagRequest;

/**
 * Interface for {@link IGitCommandFactory} implementations to provide {@link GitCommand} instances.
 *
 * @author mhoffrog
 */
public interface IGitCommandFactory {

  /**
   * @param git the {@link Git} instance
   * @return the {@link AddCommand} instance
   */
  public AddCommand getAddCommand(Git git);

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link CheckoutRequest} instance
   * @return the {@link CheckoutCommand} instance
   */
  public CheckoutCommand getCheckoutCommand(Git git, CheckoutRequest request);

  /**
   * @return the {@link CloneCommand} instance
   */
  public CloneCommand getCloneCommand();

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link CommitRequest} instance
   * @return the {@link CommitCommand} instance
   */
  public CommitCommand getCommitCommand(Git git, CommitRequest request);

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link BranchRequest} instance
   * @return the {@link CreateBranchCommand} instance
   */
  public CreateBranchCommand getCreateBranchCommand(Git git, BranchRequest request);

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link DeleteBranchRequest} instance
   * @return the {@link DeleteBranchCommand} instance
   */
  public DeleteBranchCommand getDeleteBranchCommand(Git git, DeleteBranchRequest request);

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link DeleteTagRequest} instance
   * @return the {@link DeleteTagCommand} instance
   */
  public DeleteTagCommand getDeleteTagCommand(Git git, DeleteTagRequest request);

  /**
   * @param git the {@link Git} instance
   * @return the {@link FetchCommand} instance
   */
  public FetchCommand getFetchCommand(Git git);

  /**
   * @param git the {@link Git} instance
   * @return the {@link ListTagCommand} instance
   */
  public ListTagCommand getListTagCommand(Git git);

  /**
   * @param git the {@link Git} instance
   * @return the {@link LogCommand} instance
   */
  public LogCommand getLogCommand(Git git);

  /**
   * @param git the {@link Git} instance
   * @return the {@link LsRemoteCommand} instance
   */
  public LsRemoteCommand getLsRemoteCommand(Git git);

  /**
   * @param git the {@link Git} instance
   * @return the {@link MergeCommand} instance
   */
  public MergeCommand getMergeCommand(Git git);

  /**
   * @param git the {@link Git} instance
   * @return the {@link PushCommand} instance
   */
  public PushCommand getPushCommand(Git git);

  /**
   * @param git the {@link Git} instance
   * @return the {@link ResetCommand} instance
   */
  public ResetCommand getResetCommand(Git git);

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link RevertCommitsRequest} instance
   * @return the {@link RevertCommand} instance
   */
  public RevertCommand getRevertCommand(Git git, RevertCommitsRequest request);

  /**
   * @param git     the {@link Git} instance
   * @param request the {@link TagRequest} instance
   * @return the {@link TagCommand} instance
   */
  public TagCommand getTagCommand(Git git, TagRequest request);

}
