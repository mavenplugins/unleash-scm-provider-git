# Generate SSH Keys for JUnit Tests
- Note: Nowadays ssh-keygen will create OpenSSH format private keys by default
- Create OpenSSH formatted key: RSA, 4096 bit lenth, passphrase: 'passphrase':
  - `ssh-keygen -t rsa -b 4096 -P "passphrase" -C users.noreply@github.com -f passphrase_rsa.key`
     - Private key file: `passphrase_rsa.key`
     - Public key file: `passphrase_rsa.key.pub`
- Copy `passphrase_rsa.key` -> `passphrase_rsa_openssh.key`
- Update `passphrase.key` to PEM format (otherwise JSch 0.1.54 cannot handle it):
  - `ssh-keygen -p -m pem -P "passphrase" -N "passphrase" -f passphrase_rsa.key`
- Rename `passphrase_rsa.key` -> `passphrase_rsa_pem.key`
