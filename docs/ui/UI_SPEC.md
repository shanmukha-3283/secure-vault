# Secure Vault: UI Spec (with mockups)

Original mockups for the Java Swing + FlatLaf app. The sample data is fake.
Build each screen so it **looks like its image**, then run the app and compare.

## The screens

| Screen | Image | Build phase |
|---|---|---|
| Unlock (with wrong-password state) | `docs/ui/login.png` | LoginFrame |
| Create vault (strength + checklist) | `docs/ui/create-vault.png` | LoginFrame (create mode) |
| Main window, light theme | `docs/ui/main-light.png` | MainFrame |
| Main window, dark theme | `docs/ui/main-dark.png` | MainFrame (theme toggle) |
| Add / edit item dialog | `docs/ui/add-item-dialog.png` | EntryDialog |

Editable sources are in `docs/ui/src/*.svg`.

---

## 1. Design tokens

| Token | Light | Dark |
|---|---|---|
| Window background | `#F5F6FA` | `#0F172A` |
| Surface (cards, top bar, details) | `#FFFFFF` | `#1E293B` |
| Sidebar / status bar | `#FFFFFF` | `#111827` |
| Text | `#1F2937` | `#F1F5F9` |
| Muted text | `#6B7280` | `#94A3B8` |
| Border | `#E5E7EB` | `#334155` |
| Accent (primary buttons, selection) | `#4F46E5` | `#818CF8` |
| Accent soft (selected row) | `#EEF2FF` | `#2E2A6B` |
| Danger | `#DC2626` | `#F87171` |
| Success | `#16A34A` | `#4ADE80` |
| Warning | `#D97706` | `#FBBF24` |
| Favorite star | `#F59E0B` | `#F59E0B` |

**Spacing:** use 8, 16, 24 and 32 px only.
**Corner radius:** 8 px for fields and buttons, 10 px for list rows, 16 px for cards and dialogs.
**Font:** the system default is fine. Optional: Inter via `flatlaf-fonts-inter`. Sizes: title 22 to 24 bold, section 16 bold, body 14, label 12 bold muted, hint 11 to 12.
**Icons:** simple line icons, 16 to 20 px. Use `FlatSVGIcon` (flatlaf-extras) with SVG files in `src/main/resources/icons/`.

---

## 2. Screen details

### 2.1 Unlock (`login.png`)
- Centered card, 400 px wide, on the window background.
- Lock icon in a soft accent circle, title "Secure Vault", one-line subtitle.
- Master password field with a reveal (eye) button. Placeholder-free, dots shown.
- **Wrong password:** red border plus red message under the field: "Wrong password. You can try again in 2 seconds."
- Unlock button is full width and is the default button (Enter key).
- Disable the field and button while the delay countdown runs (1s, 2s, 4s... max 30s).
- Link "New here? Create a vault" switches to the create screen.
- Footer: small lock icon and "Encrypted on this device with AES-256-GCM".

### 2.2 Create vault (`create-vault.png`)
- Same card style, taller.
- Master password field, then a 4-segment strength bar with a label (Weak / Okay / Good / Strong).
- Live checklist: 12+ characters, upper and lower case, number or symbol, not a common password. Tick turns green when met.
- Confirm password field with "Passwords match" in green.
- Warning box (amber): "There is no password recovery. If you forget it, your data is lost."
- "Create vault" stays **disabled** until the password is strong enough and both fields match.

### 2.3 Main window (`main-light.png`, `main-dark.png`)
Window 1200 x 760, minimum 900 x 600. Five zones:

| Zone | Size | Contents |
|---|---|---|
| Top bar | 60 px high | Lock icon + "Vault", search field (Ctrl+F), "+ New item" (primary), "Lock" (outline) |
| Sidebar | 220 px wide | All items, Favorites, Logins, Secure notes, Cards (with counts), FOLDERS list, Trash at the bottom |
| Item list | flexible | Header "All items" + sort label; rows 62 px high |
| Details panel | 420 px wide | Avatar + name, fields, buttons |
| Status bar | 28 px high | "12 items" on the left, "Auto-lock in 4:32" on the right |

**List row:** colored circle avatar with the first letter, name (bold), username below (muted), star if favorite, quick-copy icons on the right (copy username, copy password). Selected row: soft accent background plus a 4 px accent bar on the left.

**Details panel fields:** Username (copy), Password (dots, reveal, copy) with a small strength bar, Website (copy), Verification code (large mono digits, countdown ring, copy), Notes. Buttons: Edit (primary), Delete (red outline, moves to Trash). Small "Updated" date at the bottom.

**Empty state:** center text "No items yet. Click + New item."

### 2.4 Add / edit dialog (`add-item-dialog.png`)
- Modal, 560 px wide, dimmed background.
- Segmented control: Login | Secure note | Card. The fields below change per type.
- Fields: Name*, Username*, Password* with reveal button and a **Generate** button beside it, strength bar, Website, Folder dropdown, Favorite checkbox, Notes.
- Required fields show `*`. Errors appear in red under the field. Never use popup message boxes.
- **Save** is disabled until all required fields are valid. Cancel closes without saving.
- Esc closes the dialog. Enter saves when valid.

---

## 3. Behaviour rules

| Rule | Detail |
|---|---|
| Keyboard | Enter = default action, Esc = close dialog, Ctrl+F = focus search, Ctrl+N = new item, Ctrl+L = lock |
| Copy | Copies to clipboard, shows a "Copied" toast for 2 seconds, clears the clipboard after 20 seconds |
| Reveal | Password stays hidden by default. Hides again when the item changes or the app locks |
| Auto-lock | After 5 minutes idle: wipe the in-memory vault and show the unlock screen. Countdown in the status bar |
| Threading | All UI on the Event Dispatch Thread. Key derivation (slow by design) runs in a `SwingWorker` with a "Unlocking..." state |
| Layering | UI classes call `VaultService` only. No crypto in `ui/` |
| Theme | Toggle light and dark at runtime. Remember the choice |

---

## 4. FlatLaf setup (starting point)

Check the FlatLaf docs for the version you use, because property names can change.

```java
// App.java: before creating any window
FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", "#4F46E5"));
FlatLightLaf.setup();

UIManager.put("Component.arc", 12);
UIManager.put("Button.arc", 12);
UIManager.put("TextComponent.arc", 12);
UIManager.put("ScrollBar.thumbArc", 999);
```

```java
// Examples of FlatLaf client properties
field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search name, username or URL");
passwordField.putClientProperty(FlatClientProperties.PASSWORD_FIELD_SHOW_REVEAL_BUTTON, true);
field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR); // red border
getRootPane().setDefaultButton(unlockButton);                                              // primary button

// Switch theme at runtime
FlatLaf.setup(new FlatDarkLaf());
FlatLaf.updateUI();
```

Layout tips: `MigLayout` for forms and dialogs. For the main window use `BorderLayout` with the sidebar in WEST, details in EAST and the list in CENTER. Use a `JList` with a custom cell renderer for items (not a plain `JTable`) so the rows can look like the mockup. Draw the TOTP ring and avatars in a small custom `JComponent` with `Graphics2D` (turn on antialiasing).

Suggested classes: `ui/theme/Theme.java`, `ui/components/Avatar.java`, `StrengthBar.java`, `TotpRing.java`, `Toast.java`, `ItemListCellRenderer.java`, `SidebarPanel.java`, `DetailsPanel.java`.
