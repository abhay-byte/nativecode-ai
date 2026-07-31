# Privacy Policy — NativeCode

**Last updated:** 2026-07-31  
**App:** NativeCode (`com.ivarna.nativecode`)  
**Developer:** Ivarna / NativeCode (GitHub: [abhay-byte](https://github.com/abhay-byte))  
**Repository:** [abhay-byte/nativecode-ai](https://github.com/abhay-byte/nativecode-ai)

This Privacy Policy describes how NativeCode handles information when you use the Android app. NativeCode is a developer environment that runs a Linux guest (proot/chroot), optional X11 desktop, terminal sessions, and optional third-party AI/developer CLIs.

---

## 1. Summary

- NativeCode is **open source** and **local-first**. We do **not** collect personal data on a NativeCode cloud/backend.
- Your files, projects, packages, and credentials stay **on this device** inside the Debian **proot** or **chroot** distro (and app private storage).
- Optional features (GitHub login, AI CLI auth, package/marketplace installs, guest apt) may contact **third parties you choose** — not a NativeCode analytics service. The Debian rootfs base is packaged with the app; network is for packages/tools you request, not a silent app update.
- Auth tokens/credentials, when used, are stored **on-device** only.

---

## 2. Information we process

### 2.1 Information on your device (local)

Depending on how you use the app, the following may be stored locally:

| Category | Examples |
|----------|----------|
| App preferences | Isolation method (proot/chroot), UI/settings flags |
| Linux guest data | Debian rootfs, packages, home directory files, projects you create |
| Auth material (optional) | GitHub CLI tokens/config, AI CLI API keys or device-login session files under guest home |
| Session state | Terminal history, project paths, onboarding progress |

This local data is under your control. Clearing app storage or uninstalling the app removes app-owned data (subject to OS backup rules).

### 2.2 Information sent over the network

NativeCode initiates network requests only for features that require it, for example:

| Purpose | Typical destinations |
|---------|----------------------|
| Guest packages / optional tools | Debian/apt mirrors; marketplace or AI installer hosts **when you install**; rootfs base comes from the app package |
| GitHub authentication & API | `github.com` / GitHub API (when you connect GitHub) |
| AI CLI install or auth | Official vendor endpoints (e.g. Anthropic, OpenAI/Codex, xAI/Grok, and other tools you enable) |
| Marketplace / scripts | Hosts defined by catalog or install scripts (often raw GitHub) |
| Package managers | npm registry, nvm installers, apt repositories (when you install tools) |

**We do not sell personal information.** Third parties process data under **their** privacy policies when you authenticate or download software from them.

### 2.3 Account identifiers

If you sign in to GitHub or an AI CLI:

- Usernames, emails, or account labels may appear in local CLI config or UI.
- OAuth/device codes and tokens are handled to complete login and then stored as required by those tools.
- NativeCode does not require a separate “NativeCode cloud account.”

### 2.4 Permissions

NativeCode may request Android permissions for its features (e.g. notifications for foreground services that keep terminal/AI sessions alive, network access). Permissions are used only for the stated in-app purpose.

---

## 3. How we use information

- Provide and maintain the developer environment (terminal, guest Linux, X11).
- Authenticate optional third-party CLIs **at your request**.
- Install guest packages, marketplace components, and optional AI CLIs **when you trigger install/setup** (base rootfs is app-local).
- Show status, storage size, and connection state in Settings.

We do **not** use on-device credentials for advertising or cross-app tracking.

---

## 4. Sharing

We do not sell or rent your data. Data leaves the device when:

1. You authenticate to a third party (GitHub, AI vendors).
2. You install guest packages/scripts from remote hosts (or use optional AI installers) after you choose to.
3. The OS or a tool you run inside the guest makes its own network calls.

Third-party services are independent controllers for data they receive. Review their policies before connecting accounts.

---

## 5. Retention

- **Local app & guest data:** retained until you delete it, clear storage, or uninstall.
- **Tokens/keys:** retained until you sign out (where supported), overwrite config, clear guest home, clear app data, or uninstall.
- **No separate NativeCode cloud retention** is operated by this app as of the date above.

---

## 6. Security

- Traffic to third parties generally uses HTTPS where those services support it.
- Credentials live in app/guest storage; protect your device with a lock screen and avoid untrusted apps with backup/debug access.
- `allowBackup` and OS backup behavior may copy app files depending on device settings—treat device backups as sensitive if you store tokens.

No method of electronic storage is 100% secure. Use official install/auth flows only.

---

## 7. Children’s privacy

NativeCode is a developer tool intended for adults and professional use. It is **not directed at children under 13** (or the age required by local law). Do not use the app if you are under the applicable age threshold.

---

## 8. AI-generated content

Optional AI CLIs run as third-party tools inside the guest environment. Outputs are generated by those vendors or local models you install. You are responsible for how you use those tools and for complying with each vendor’s terms.

**In-app:** Settings → **AI Safety & Report** — third-party ToS/AUP links, per-vendor report forms, and a developer report path (`mailto:zenithblue.dev@gmail.com`). Prefer vendor forms for model-output concerns; use the developer email for app-related safety awareness.

---

## 9. Your choices & deletion

| Action | Effect |
|--------|--------|
| Settings → GitHub Account → **LOGOUT** | Signs out GitHub CLI for current isolation method |
| Settings → AI CLI tools → per-tool **LOGOUT** or **CLEAR ALL AI CREDENTIALS** | Removes local AI tokens/keys for that method |
| Settings → AI CLI tools → **CLEAR SUITE FLAG** | Hides AI launchers until reinstall (bins may remain) |
| Settings → **AI Safety & Report** | In-app deletion guidance + vendor links |
| Delete projects / files in the guest | Removes those files from guest storage |
| **Clear app storage** (Android App info) | Wipes app data including guest rootfs under app storage (proot path) |
| **Uninstall** | Removes the app and its private data |
| Chroot installs on shared storage | May need explicit uninstall/cleanup from Chroot Settings if used |

For account data held only by GitHub or AI vendors, manage deletion in those services’ account settings.

---

## 10. International users

Processing occurs on your device and at third-party servers wherever those providers operate. By using network features you understand data may cross borders under those providers’ terms.

---

## 11. Changes to this policy

We may update this file in the repository. The **Last updated** date at the top will change. Material changes should be reflected in the Play Console privacy policy URL and Data safety form when the app is distributed on Google Play.

---

## 12. Contact

Questions about this Privacy Policy or NativeCode data practices:

- **Email:** [zenithblue.dev@gmail.com](mailto:zenithblue.dev@gmail.com) (privacy, AI report, app concerns)
- **GitHub issues:** [abhay-byte/nativecode-ai](https://github.com/abhay-byte/nativecode-ai/issues)
- **Developer:** abhay-byte (Ivarna / NativeCode)

---

## 13. Play Store note

When published on Google Play, the same policy text (or a hosted HTML copy of it) will be linked from:

1. Play Console → App content → Privacy policy  
2. In-app Settings → Privacy Policy  

This document lives at:

`https://github.com/abhay-byte/nativecode-ai/blob/master/docs/privacy-policy.md`
