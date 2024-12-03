# git_ssh_test_repo

## OpenSSH Key Generation
- Create keys: `ssh-keygen -t ecdsa -b 521 -f ./git_ssh_test_repo_ecdsa521.key -v -P "passphrase" -C users.noreply@github.com`
  - Note: Leave option `-P`, if passphrase has to be entered manually via commandline input
  - Files created:
    - Private key: `git_ssh_test_repo.key` (OpenSSH format)
	- Public key: `git_ssh_test_repo.key.pub`
- Update passphrase: `ssh-keygen -p -f ./git_ssh_test_repo.key -v -P "oldPassphrase" -N "newPassphrase"`

### Add Private Key in PEM Format
- Copy `git_ssh_test_repo_ecdsa521.key` -> `git_ssh_test_repo_ecdsa521_openssh.key`
- Create PEM format private key (otherwise JSch 0.1.54 cannot handle it):
  - `ssh-keygen -p -m pem -P "passphrase" -N "passphrase" -f git_ssh_test_repo_ecdsa521.key`
- Rename `git_ssh_test_repo_ecdsa521.key` -> `git_ssh_test_repo_ecdsa521_pem.key`


## Git Clone (with key usage from key file)
- Git clone: `git -c core.sshCommand="ssh -i ./git_ssh_test_repo_ecdsa521.key -o IdentitiesOnly=yes" clone git@github.com:mavenplugins/git_ssh_test_repo.git`
- After initial clone:
  - Git config local repo for `core.sshCommand`: `git config core.sshCommand 'ssh -i ./git_ssh_test_repo_ecdsa521.key -o IdentitiesOnly=yes'`
