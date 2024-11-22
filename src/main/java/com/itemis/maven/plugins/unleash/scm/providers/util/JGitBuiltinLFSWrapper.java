package com.itemis.maven.plugins.unleash.scm.providers.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.hooks.PrePushHook;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lfs.LfsPrePushHook;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.LfsFactory;

public class JGitBuiltinLFSWrapper extends LfsFactory {

  private final BuiltinLFS builtinLFS;

  private JGitBuiltinLFSWrapper(BuiltinLFS builtinLFS) {
    this.builtinLFS = builtinLFS;
  }

  /**
   * Activates the built-in LFS support.
   */
  public static void register() {
    BuiltinLFS.register();
    setInstance(new JGitBuiltinLFSWrapper((BuiltinLFS) getInstance()));
  }

  @Override
  public boolean isAvailable() {
    return this.builtinLFS.isAvailable();
  }

  @Override
  public ObjectLoader applySmudgeFilter(Repository db, ObjectLoader loader, Attribute attribute) throws IOException {
    return this.builtinLFS.applySmudgeFilter(db, loader, attribute);
  }

  @Override
  public LfsInputStream applyCleanFilter(Repository db, InputStream input, long length, Attribute attribute)
      throws IOException {
    return this.builtinLFS.applyCleanFilter(db, input, length, attribute);
  }

  @Override
  public @Nullable PrePushHook getPrePushHook(Repository repo, PrintStream outputStream) {
    if (isEnabled(repo)) {
      return new LfsPrepushHookWithoutWarning(repo, outputStream);
    }
    return null;
  }

  @Override
  public boolean isEnabled(Repository db) {
    return this.builtinLFS.isEnabled(db);
  }

  @Override
  public LfsInstallCommand getInstallCommand() {
    return this.builtinLFS.getInstallCommand();
  }

  public static class LfsPrepushHookWithoutWarning extends LfsPrePushHook {

    private LfsPrepushHookWithoutWarning(Repository repo, PrintStream outputStream) {
      super(repo, outputStream);
    }

    @Override
    public boolean isNativeHookPresent() {
      // Suppress warning message about conflict with existing pre-push hook
      // if (super.isNativeHookPresent()) {
      // System.out.println("##### Native pre-push hook is present - warning suppressed!");
      // }
      return false;
    }

  }

}
