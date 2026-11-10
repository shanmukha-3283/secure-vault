# Threat model

Who are we defending the vault file against, what can they do, and what does
the design actually buy you. Read together with `LIMITATIONS.md`.

## Assets

| Asset | Where | Sensitivity |
|---|---|---|
| Entry metadata | `vault.dat` ciphertext | Account labels, usernames, notes, URLs |
| Passwords | `vault.dat` ciphertext | High: reuse of a vault password would compromise the linked account |
| TOTP secrets | `vault.dat` ciphertext | High: possession = second factor for that account |
| Master password | RAM only, wiped clones | The single factor for everything above |

## Trust boundary

One machine, one user, one file. Anything outside the JVM — other apps, the
window system, the OS, the disk, the network — is **not trusted**. The
attacker we defend against by design:

> **A1 — offline attacker with a copy of `vault.dat`** (stolen USB, synced
> backup, disposed disk) who has unlimited time to try passwords offline.

Everything else below is either explicitly out of scope or explicitly a
limitation.

## Mitigations (by protocol)

- **PBKDF2-HMAC-SHA256, 600,000 iterations**, 16-byte random salt → a fast
  GPU rig pays real money per guess. Every retry after a fail is capped at
  ~1 human second on hardware from the last decade.
- **AES-256-GCM**, new 12-byte IV per save, 128-bit tag → the same
  plaintext is never re-encrypted under the same key and nonce, which is
  the failure GCM cannot survive.
- **Header (magic + version + salt + IV) bound as GCM AAD** → an attacker
  cannot swap the salt/IV header of one vault onto another file and hope
  for version-downgrade or salt-fixation misfire; the tag will fail.
- **No password hash on disk.** The current password is proven by
  generating a tag with the derived key (same as unlocking). There is
  nothing to crack that is not the file itself.
- **Atomic save, `vault.dat.tmp` → `ATOMIC_MOVE`** → a crash mid-save keeps
  the previous ciphertext whole instead of a truncated vault.
- **Wrong-password slows down politely** — exponential retry backoff
  (2 s → 60 s cap) targets the interactive attacker (A2 below), never
  locks permanently: you cannot lock yourself out.

## Explicitly mitigated UI-level threats

| Threat | Defence |
|---|---|
| Auto-lock forgotten → session left open | Hard wipe + unlock screen after 5 idle minutes (mouse + keyboard) |
| Screen passers-by see passwords | Reveal hides by default, dots; reveal turns off when the item changes or the app locks |
| Clipboard reader on the same desktop | Copy clears the clipboard after 20 s; window for the attack is short but real |
| Tampering with the file | GCM tag fails → same error as wrong password, and a save after a failed decrypt refuses to overwrite |

## Out of scope (these defeat the app; see LIMITATIONS.md)

| Threat | Why it is out |
|---|---|
| Malware on the same machine (keyloggers, clipboard or memory scrapers) | Runs outside the vault's boundary and with the same privileges |
| Memory/swap scraping while unlocked | Java does not offer page locking; wiped objects still live in the collector |
| Shoulder surfing, cameras | Nothing we can do from software |
| Losing the file or the password | No recovery exists by design |
| False amnesia — the attacker replaces your copy today and asks later | GCM proves *a file* is intact, not *which vintage* |
| Forensics on free blocks/SSD cells after rekey or deletion | Wear leveling is below the filesystem's control |
| Multiple users or devices | Out of product scope: single user, single device, no sync |

## Attack cost estimate (A1)

For a distinct 12-character random-class password, the expected
brute-force effort at the stated PBKDF2 settings vastly exceeds a realistic
attacker's budget; for a reused dictionary password (`Password1234!`), the
answer is "hours on rented silicon" no matter what the UI says — which is
why the creator flow forces length + mixed characters and the strength
meter gates the create/rotate dialogs.

## Not audited

This model (and every claim in it) is unreviewed. Third-party cryptanalysis
is not something an educational project can purchase. Use accordingly.
