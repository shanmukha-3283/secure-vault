# Limitations

This is an educational project. It is honest about what it cannot do. Read
this file before deciding to store anything real.

## Fundamental limits

- **No recovery, ever.** The master password is never stored anywhere and
  the file cannot be brute-forced into giving up. Forgetting it means the
  vault and everything inside is gone. There is no backdoor, no reset and
  no support ticket to open.
- **A changed password cannot shrink the past.** Changing the master
  password re-encrypts the whole vault with a new salt and IV, but the old
  ciphertext of previous saves may still exist in free disk blocks or SSD
  wear-leveling cells long after the file was atomically replaced. Disk
  encryption at the OS level (LUKS, BitLocker) is the proper fix for that.
- **Secrets live in memory while unlocked.** Java copies strings, keeps
  references invisible to the code and garbage-collects when it wants to.
  Passwords are wiped when possible (`Arrays.fill`) but a single forgotten
  `String` can keep a secret readable until that memory is reused. `mlock`
  and page-locking are not available to a Swing app, so the OS may swap
  decrypted vault data to disk.
- **The master password is typed in the clear.** Swing text fields do not
  respond to secure memory, so keystrokes pass through the window system
  before reaching the app.

## Environmental limits

- **Malware wins.** A keylogger, clipboard watcher or screen capture on the
  same machine defeats this vault completely. The vault only protects
  against an attacker holding the *file*, never an attacker holding the
  *session*.
- **Clipboard exposure window.** "Copy" shares the clipboard with every
  other app. The 20-second auto-clear narrows that window; it cannot close
  it. Copy before a browser takes focus, not after.
- **Single user, single device, no sync.** The vault is a file. Sharing it
  through Dropbox or a backup drive is unencrypted sync on the other side;
  backups are as targetable as the original.
- **Total loss is possible.** The vault is one small file. A corrupted disk
  without a backup loses everything. The flip side: no cloud account to
  breach.

## Security design limits

- **Tamper detection is for the honest case.** AES-GCM proves the file is
  authentic, meaning the wrong password and a modified file produce the
  same error, quietly. A patient attacker can still copy your `vault.dat`
  today, replace it with anything and let it re-appear later; GCM will not
  tell you whether the file *they* handed you is the file *you* wrote.
- **Idle screens leak.** Auto-lock (5 minutes, wiped session) applies to
  the app only. The OS screen lock, a shoulder surfer or an unlocked
  screensaver are out of the vault's control.
- **Weak passwords pass some rules.** The strength meter is a heuristic,
  not entropy math. `Password1234!` and a 24-character random string both
  say "Strong". Choices like that are on you.

## Not audited

No third party has reviewed the cryptography, this code or anything around
it. Treat this as a learning project about crypto plumbing, **not** as the
vault behind which you keep, e.g., anything you actually care about.

---

*Status: educational. Not for real production use.*
