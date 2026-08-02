# Foreground Service `specialUse` — Play Console declaration

**App:** NativeCode (`com.zenithblue.nativecode`)  
**Type:** `specialUse`  
**Services:** `BackgroundService`, `AppTerminalService`, `ProjectTerminalService`  
**Demo video:** [`fgs-special-use-demo.mp4`](./fgs-special-use-demo.mp4) — **~50s live UI**  
Path: open app → Terminal → Debian Shell (session + FGS) → shade shows **NativeCode — Terminal** → Settings → **START XFCE DESKTOP** → shade shows **Desktop Session** + Terminal together.

## Justification (≤ 300 characters — paste into Console)

```
NativeCode keeps user-started Linux terminals, per-project shells, and optional XFCE desktop (Termux-X11) alive after leaving the app. specialUse fits this developer environment (not media/location). Ongoing notifications show status; closing sessions or Stop Desktop ends the service.
```

**Character count:** 285

## User-visible stop paths

| Session | Notification title | How user stops |
|---------|-------------------|----------------|
| App terminal | NativeCode — Terminal | Close all terminal tabs in app |
| Project terminal | NativeCode — Project Terminal | Close project terminal tabs |
| XFCE desktop | NativeCode — Desktop Session | Settings hub → Stop XFCE Desktop |

## Manifest properties

Each FGS declares `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with a short subtype string (see `app/src/main/AndroidManifest.xml`).

## Reviewer notes

- Open-source, local-first: guest data stays in proot/chroot on device.
- FGS starts only after user opens a terminal session or starts XFCE.
- `POST_NOTIFICATIONS` requested on Android 13+ so the ongoing notification is visible.
