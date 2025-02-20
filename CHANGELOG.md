# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

<!-- Format restrictions - see https://common-changelog.org and https://keepachangelog.com/ for details -->
<!-- Each Release must start with a line for the release version of exactly this format: ## [version] -->
<!-- The subsequent comment lines start with a space - not to irritate the release scripts parser!
 ## [major.minor.micro]
 <empty line> - optional sub sections may follow like:
 ### Added:
 - This feature was added
 <empty line>
 ### Changed:
 - This feature was changed
 <empty line>
 ### Removed:
 - This feature was removed
 <empty line>
 ### Fixed:
 - This issue was fixed
 <empty line>
 <empty line> - next line is the starting of the previous release
 ## [major.minor.micro]
 <empty line>
 <...>
 !!! In addition the compare URL links are to be maintained at the end of this CHANGELOG.md as follows.
     These links provide direct access to the GitHub compare vs. the previous release.
     The particular link of a released version will be copied to the release notes of a release accordingly.
     At the end of this file appropriate compare links have to be maintained for each release version in format:
 
  +-current release version
  |
  |                   +-URL to this repo                  previous release version tag-+       +-current release version tag
  |                   |                                                                |       |
 [major.minor.micro]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/vM.N.u..vM.N.u
-->
<!--
## [Unreleased]

### 🚨 Removed
- TBD

### 💥 Breaking
- TBD

### 📢 Deprecated
- TBD

### 🚀 New Features
- TBD

### 🐛 Fixes
- TBD

### ✨ Improvements
- TBD

### 🔧 Internal Changes
- TBD

### 🚦 Tests
- TBD

### 📦 Updates
- TBD

### 🔒 Security
- TBD

### 📝 Documentation Updates
- TBD

-->

## [Unreleased]

### Changes
- TBD


## [3.3.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.3.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.3.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/3.3.0)

### Summary
- Enable JGit BuiltinLFS by option `-DscmGit.enableJGitBuiltinLFS`<br>
  or if git config `filter.lfs.useJGitBuiltin` is set `true` - #7<br>
- The SSH private key can be provided as file also
- 🚀 Make SSH connections work with recent key encodings

### 🚀 New Features
- Enable JGit BuiltinLFS by option `-DscmGit.enableJGitBuiltinLFS`<br>
  or if git config `filter.lfs.useJGitBuiltin` is set `true` - #7<br>
- If the private key string is of file URL protocol format `file:<filename>`, then the private key is read from that file.
- Fix vulnerability warning on `jgit` dependency

### 🔒 Security
- fix vulnerability warning for `jgit` dependency

### 📦 Updates
- pom.xml:
  - bump `jgit` version to `5.13.3.202401111512-r`
  - add dependency to `org.eclipse.jgit:org.eclipse.jgit.lfs`
  - replace `jsch` dependency by `org.apache.sshd 2.14.0` dependencies
  - bump `slf4j` version to `1.7.36`
  - bump `junit` version to `4.13.2`
  - add explicit `gson` dependency version `2.8.9` to resolve vulnerability warning

### 🔧 Internal Changes
- ScmProviderGit.java:
  - restructure code to allow specific JUnit testing without a repo being checked out
  - add log for JGit built in enabled status
- GitUtil.java:
  - add method enableJGitBuiltinLFSIfDefined(final Repository repository)
  - call this method from cTor
  - enhance cTor by logger parameter

### 🚦 Tests
- GitSshSessionFactoryTest.java:
  - Revive commented tests
  - add test resources required
- GitScmTestUtil.java:
  - add as helper util for tests


## [3.2.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.2.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.2.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/3.2.0)

### Summary
- Enable JGit BuiltinLFS by option `-DscmGit.enableJGitBuiltinLFS`<br>
  or if git config `filter.lfs.useJGitBuiltin` is set `true` - #7<br>

### 🚀 New Features
- Enable JGit BuiltinLFS by option `-DscmGit.enableJGitBuiltinLFS`<br>
  or if git config `filter.lfs.useJGitBuiltin` is set `true` - #7<br>

### 📦 Updates
- pom.xml:
  - add dependency to `org.eclipse.jgit:org.eclipse.jgit.lfs`
  - bump junit version to `4.13.1`

### 🔧 Internal Changes
- ScmProviderGit.java:
  - restructure code to allow specific JUnit testing without a repo being checked out
  - add log for JGit built in enabled status
- GitUtil.java:
  - add method enableJGitBuiltinLFSIfDefined(final Repository repository)
  - call this method from cTor
  - enhance cTor by logger parameter

### 🚦 Tests
- GitSshSessionFactoryTest.java:
  - Revive commented tests
  - add test resources required
- GitScmTestUtil.java:
  - add as helper util for tests


## [3.1.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.1.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.1.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/3.1.0)

### Summary
- Fix issue raised by workflow step `SetNextDevVersion` for Git SCM projects. - #6<br>

### 💥 Breaking
  ❗👉 Requires `unleash-scm-provider-api` version `3.2.0` or later❗

### 🐛 Fixes
- Fix issue raised by workflow step `SetNextDevVersion` for Git SCM projects. - #6<br>
  If the Maven project base dir is within a sub folder of the git checkout directory,
  then the next dev modified POMs did not get recognized as changed files to be commited.<br>

### 📦 Updates
- pom.xml:
  - update dependency `unleash-scm-provider-api` to version `3.2.0`
- ScmProviderGit.java:
  - consider relative working dir parent to Git work tree for files added to commit


## [3.0.1]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.0.1 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.0.1)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/3.0.1)

### Summary
- Update dependency `unleash-scm-provider-api` to scope `provided`
- No further functional or code change

### Updates
- pom.xml:
  - update dependency `unleash-scm-provider-api` to scope `provided`


## [3.0.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![3.0.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=3.0.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/3.0.0)

### Summary
- Update dependency to `unleash-scm-provider-api 3.0.0`
  - => work for Java 8, 11, 17 and 21 by CDI WELD 4.0.3.Final with Jakarta Inject API
- Provide probable output messages from remote server for push commands - #4
- Add UnleashGitRevertCommand to ensure `scmMessagePrefix` is considered for revert log messages if it is set - #5

### Compatibility
- 👉 This release requires to be used with `unleash-maven-plugin >= v3.0.0` only! It will NOT work with former versions of `unleash-maven-plugin`<br>
  Reason: CDI dependencies did have been changed from Javax to Jakarta EE

### Updates
- pom.xml:
  - remove explicit dependency to `com.jcraft:jsch`
  - exclude dependency to `com.jcraft:jsch` from `com.jcraft:jsch.agentproxy.jsch` since it overlaps with the dependency from `org.eclipse.jgit:org.eclipse.jgit`
  - refer to `unleash-scm-provider-api 3.0.0`

- ScmProviderGit.java:
  - move push result handling to new method callPush(...)
  - re-factor all places where push.call() was used to make use of callPush(...)

- UnleashGitRevertCommand.java:
  - Add UnleashGitRevertCommand.java as original copy of the RevertCommand class of JGit version 5.0.2.201807311906-r
  - replace original cTor with public cTor taking scmMessagePrefix
  - prepend revert commit log message with scmMessagePrefix, if this is not blank
  - fix incompatible types compiler issue

- DefaultGitCommandFactory.java:
  - provide UnleashGitRevertCommand instead of original JGit RevertCommand


## [2.4.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![2.4.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=2.4.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/2.4.0)

### Summary
- Enhance the tag comment to prepend with scmMessagePrefix if this is set - #2
- Leverage injection of an `IGitCommandFactory` to simplify customization of JGit commands - #3

### Updates
- pom.xml:
  - refer to `unleash-scm-provider-api 2.11.0`

- ScmProviderGit.java:
  - method tag(TagRequest request):
    - prepend the PreTagCommitMessage with scmMessagePrefix
      if this is set.
  - add injected field IGitCommandFactory gitCommandFactory
    - get all GitCommand by this factory


## [2.3.0]
<!-- !!! Align version in badge URLs as well !!! -->
[![2.3.0 Badge](https://img.shields.io/nexus/r/io.github.mavenplugins/unleash-scm-provider-git?server=https://s01.oss.sonatype.org&label=Maven%20Central&queryOpt=:v=2.3.0)](https://central.sonatype.com/artifact/io.github.mavenplugins/unleash-scm-provider-git/2.3.0)

### Summary
- Initial release of this artifact with new groupId `io.github.mavenplugins`
- Codewise identical with `com.itemis.maven.plugins:unleash-scm-provider-git:2.3.0`<br>No more features nor changes
- Update Java compile version to `1.8` to comply with dependency `io.github.mavenplugins:unleash-scm-provider-api:2.10.0`
- Update m2e launch config to Eclipse 2023-12
- Released to Maven Central

### Updates
- pom.xml:
  - update parent POM reference
  - update groupId to `io.github.mavenplugins`
  - update `version.java` `1.6` -> `1.8`
  - update URLs to fit with new repo location

- README.md:
  - add URLs for build badges

- UnleashGitMerger.java:
  - fix JavaDoc warnings

- InMemoryIdentity.java:
  - fix deprecation warning


<!--
## []

### NeverReleased
- This is just a dummy placeholder to make the parser of GHCICD/release-notes-from-changelog@v1 happy!
-->

[Unreleased]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v3.3.0..HEAD
[3.3.0]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v3.2.0..v3.3.0
[3.2.0]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v3.1.0..v3.2.0
[3.1.0]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v3.0.1..v3.1.0
[3.0.1]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v3.0.0..v3.0.1
[3.0.0]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v2.4.0..v3.0.0
[2.4.0]: https://github.com/mavenplugins/unleash-scm-provider-git/compare/v2.3.0..v2.4.0
[2.3.0]: https://github.com/mavenplugins/unleash-scm-provider-git/releases/tag/v2.3.0
