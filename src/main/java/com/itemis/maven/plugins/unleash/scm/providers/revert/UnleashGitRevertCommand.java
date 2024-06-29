/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.itemis.maven.plugins.unleash.scm.providers.revert;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeMessageFormatter;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;

/**
 * This is a copy of the {@link RevertCommand} class of JGit version 5.0.2.201807311906-r.
 * It is extended to prepend the commit log messages with {@code scmMessagePrefix} if this is set.
 */
public class UnleashGitRevertCommand extends RevertCommand {
  private List<Ref> commits = new LinkedList<>();

  private String ourCommitName = null;

  private List<Ref> revertedRefs = new LinkedList<>();

  private MergeResult failingResult;

  private List<String> unmergedPaths;

  private MergeStrategy strategy = MergeStrategy.RECURSIVE;

  private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

  private final String scmMessagePrefix;

  public UnleashGitRevertCommand(Repository repo, String scmMessagePrefix) {
    super(repo);
    this.scmMessagePrefix = scmMessagePrefix;
  }

  /**
   * {@inheritDoc}
   * <p>
   * Executes the {@code revert} command with all the options and parameters
   * collected by the setter methods (e.g. {@link #include(Ref)} of this
   * class. Each instance of this class should only be used for one invocation
   * of the command. Don't call this method twice on an instance.
   */
  @Override
  public RevCommit call() throws NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException,
      WrongRepositoryStateException, GitAPIException {
    RevCommit newHead = null;
    checkCallable();

    try (RevWalk revWalk = new RevWalk(this.repo)) {

      // get the head commit
      Ref headRef = this.repo.exactRef(Constants.HEAD);
      if (headRef == null) {
        throw new NoHeadException(JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
      }
      RevCommit headCommit = revWalk.parseCommit(headRef.getObjectId());

      newHead = headCommit;

      // loop through all refs to be reverted
      for (Ref src : this.commits) {
        // get the commit to be reverted
        // handle annotated tags
        ObjectId srcObjectId = src.getPeeledObjectId();
        if (srcObjectId == null) {
          srcObjectId = src.getObjectId();
        }
        RevCommit srcCommit = revWalk.parseCommit(srcObjectId);

        // get the parent of the commit to revert
        if (srcCommit.getParentCount() != 1) {
          throw new MultipleParentsNotAllowedException(
              MessageFormat.format(JGitText.get().canOnlyRevertCommitsWithOneParent, srcCommit.name(),
                  Integer.valueOf(srcCommit.getParentCount())));
        }

        RevCommit srcParent = srcCommit.getParent(0);
        revWalk.parseHeaders(srcParent);

        String ourName = calculateOurName(headRef);
        String revertName = srcCommit.getId().abbreviate(7).name() + " " + srcCommit.getShortMessage(); //$NON-NLS-1$

        ResolveMerger merger = (ResolveMerger) this.strategy.newMerger(this.repo);
        merger.setWorkingTreeIterator(new FileTreeIterator(this.repo));
        merger.setBase(srcCommit.getTree());
        merger.setCommitNames(new String[] { "BASE", ourName, revertName }); //$NON-NLS-1$

        String shortMessage = "Revert \"" + srcCommit.getShortMessage() //$NON-NLS-1$
            + "\""; //$NON-NLS-1$
        String newMessage = shortMessage + "\n\n" //$NON-NLS-1$
            + "This reverts commit " + srcCommit.getId().getName() //$NON-NLS-1$
            + ".\n"; //$NON-NLS-1$
        // @patch Unleash: prepend scmMessagePrefix if it is not blank {
        if (StringUtils.isNotBlank(this.scmMessagePrefix)) {
          newMessage = this.scmMessagePrefix
              + (StringUtils.endsWith(this.scmMessagePrefix, System.lineSeparator() + System.lineSeparator())
                  || StringUtils.endsWith(this.scmMessagePrefix, "\n\n")
                      ? StringUtils.replace(newMessage, "\n\n", "\n", 1)
                      : newMessage);
        }
        // @patch Unleash }
        if (merger.merge(headCommit, srcParent)) {
          if (AnyObjectId.equals(headCommit.getTree().getId(), merger.getResultTreeId())) {
            continue;
          }
          DirCacheCheckout dco = new DirCacheCheckout(this.repo, headCommit.getTree(), this.repo.lockDirCache(),
              merger.getResultTreeId());
          dco.setFailOnConflict(true);
          dco.setProgressMonitor(this.monitor);
          dco.checkout();
          try (Git git = new Git(getRepository())) {
            newHead = git.commit().setMessage(newMessage).setReflogComment("revert: " + shortMessage) //$NON-NLS-1$
                .call();
          }
          this.revertedRefs.add(src);
          headCommit = newHead;
        } else {
          this.unmergedPaths = merger.getUnmergedPaths();
          Map<String, MergeFailureReason> failingPaths = merger.getFailingPaths();
          // @patch Unleash: fix incompatible types error
          Map<String, org.eclipse.jgit.merge.MergeResult<?>> lowLevelResults = merger.getMergeResults();
          if (failingPaths != null) {
            this.failingResult = new MergeResult(null, merger.getBaseCommitId(),
                new ObjectId[] { headCommit.getId(), srcParent.getId() }, MergeStatus.FAILED, this.strategy,
                // @patch Unleash: fix incompatible types error
                // merger.getMergeResults(), failingPaths, null);
                lowLevelResults, failingPaths, null);
          } else {
            this.failingResult = new MergeResult(null, merger.getBaseCommitId(),
                new ObjectId[] { headCommit.getId(), srcParent.getId() }, MergeStatus.CONFLICTING, this.strategy,
                // @patch Unleash: fix incompatible types error
                // merger.getMergeResults(), failingPaths, null);
                lowLevelResults, failingPaths, null);
          }
          if (!merger.failed() && !this.unmergedPaths.isEmpty()) {
            String message = new MergeMessageFormatter().formatWithConflicts(newMessage, merger.getUnmergedPaths());
            this.repo.writeRevertHead(srcCommit.getId());
            this.repo.writeMergeCommitMsg(message);
          }
          return null;
        }
      }
    } catch (IOException e) {
      throw new JGitInternalException(
          MessageFormat.format(JGitText.get().exceptionCaughtDuringExecutionOfRevertCommand, e), e);
    }
    return newHead;
  }

  /**
   * Include a {@code Ref} to a commit to be reverted
   *
   * @param commit
   *                 a reference to a commit to be reverted into the current head
   * @return {@code this}
   */
  @Override
  public RevertCommand include(Ref commit) {
    checkCallable();
    this.commits.add(commit);
    return this;
  }

  /**
   * Include a commit to be reverted
   *
   * @param commit
   *                 the Id of a commit to be reverted into the current head
   * @return {@code this}
   */
  @Override
  public RevertCommand include(AnyObjectId commit) {
    return include(commit.getName(), commit);
  }

  /**
   * Include a commit to be reverted
   *
   * @param name
   *                 name of a {@code Ref} referring to the commit
   * @param commit
   *                 the Id of a commit which is reverted into the current head
   * @return {@code this}
   */
  @Override
  public RevertCommand include(String name, AnyObjectId commit) {
    return include(new ObjectIdRef.Unpeeled(Storage.LOOSE, name, commit.copy()));
  }

  /**
   * Set the name to be used in the "OURS" place for conflict markers
   *
   * @param ourCommitName
   *                        the name that should be used in the "OURS" place for conflict
   *                        markers
   * @return {@code this}
   */
  @Override
  public RevertCommand setOurCommitName(String ourCommitName) {
    this.ourCommitName = ourCommitName;
    return this;
  }

  private String calculateOurName(Ref headRef) {
    if (this.ourCommitName != null) {
      return this.ourCommitName;
    }

    String targetRefName = headRef.getTarget().getName();
    String headName = Repository.shortenRefName(targetRefName);
    return headName;
  }

  /**
   * Get the list of successfully reverted {@link org.eclipse.jgit.lib.Ref}'s.
   *
   * @return the list of successfully reverted
   *         {@link org.eclipse.jgit.lib.Ref}'s. Never <code>null</code> but
   *         maybe an empty list if no commit was successfully cherry-picked
   */
  @Override
  public List<Ref> getRevertedRefs() {
    return this.revertedRefs;
  }

  /**
   * Get the result of a merge failure
   *
   * @return the result of a merge failure, <code>null</code> if no merge
   *         failure occurred during the revert
   */
  @Override
  public MergeResult getFailingResult() {
    return this.failingResult;
  }

  /**
   * Get unmerged paths
   *
   * @return the unmerged paths, will be null if no merge conflicts
   */
  @Override
  public List<String> getUnmergedPaths() {
    return this.unmergedPaths;
  }

  /**
   * Set the merge strategy to use for this revert command
   *
   * @param strategy
   *                   The merge strategy to use for this revert command.
   * @return {@code this}
   * @since 3.4
   */
  @Override
  public RevertCommand setStrategy(MergeStrategy strategy) {
    this.strategy = strategy;
    return this;
  }

  /**
   * The progress monitor associated with the revert operation. By default,
   * this is set to <code>NullProgressMonitor</code>
   *
   * @see NullProgressMonitor
   * @param monitor
   *                  a {@link org.eclipse.jgit.lib.ProgressMonitor}
   * @return {@code this}
   * @since 4.11
   */
  @Override
  public RevertCommand setProgressMonitor(ProgressMonitor monitor) {
    if (monitor == null) {
      monitor = NullProgressMonitor.INSTANCE;
    }
    this.monitor = monitor;
    return this;
  }
}
