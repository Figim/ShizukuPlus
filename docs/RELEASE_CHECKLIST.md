# Stable release checklist (next stable after v13.7.0)

Built 2026-10-24 from the device / screenshot checklists scattered through `AI_DEVLOG.md`
(2026-10-22 → 2026-10-24) plus the release mechanics. Tick items as they're verified; anything
that fails becomes an issue or a fix before the stable dispatch.

## 0. Blockers (all must be done)

- [ ] **Unit tests green in CI.** New `unit-tests` job in `app.yml`. First run includes the 4
      Robolectric suites that can't run on the phone (OnePromptScenarios, VendorBatteryRestriction,
      WatchdogBatteryExemption, ActivityLogDao).
- [ ] **Emulator smoke tests green.** `smoke-test` job (JniSmokeTest + ScreenSmokeTest), dispatch only.
- [ ] **On-device smoke pass:** `bash scripts/dev/device-smoke.sh` (keeps your data).
- [ ] **Release (R8) build on the phone:** see §2. Needs an uninstall if the phone has a debug build,
      so **export a backup first**.
- [ ] **Merge origin/master** (`212d3a2a` README, `5c0a0716` az #619). Resolve values-az with the
      LOCAL side, then confirm no `*_value`/`*_values` arrays came back in any locale and none of
      the strings removed in `ef5bfa82` returned.
- [ ] `api` submodule `e7d1773` is on its remote (verified 2026-10-24), so the parent push is safe.
- [ ] Fold `## [Unreleased]` in `CHANGELOG.md` into the release section. Version follows the
      AGENTS.md fork-versioning rule (no upstream version bump without merging upstream).
- [ ] Push only on explicit OK. Push to master = **prerelease**; stable = `workflow_dispatch` on
      master with `prerelease=false`.
- [ ] After release: close #623 / #624 (fix `9a811eee` ships here), and reply on #626 (`ef092a45`).

## 1. Crash / data safety (P0, device)

- [ ] Cold start: "Authorized N applications" from the first frame (no 0 flash).
- [ ] Settings search: type several queries; every result shows its icon (#623/#624).
- [ ] Upgrade a build with **Rounded edges OFF** → Corner rounding shows Sharp and corners stay sharp.
- [ ] Restore an **old backup** with `rounded_edges_enabled=false` → Sharp. Backup → restore round trip
      shows a toast then the restart hint (not two snackbars).
- [ ] Theme changes: Material screen transitions **on and off** (watch for #483 on Android 16: black
      screen / crash after a theme change).
- [ ] Known open: `custom_accent` / `use_system_color` only re-theme XML screens on the next resume
      (`applyTheme(requiresRecreate = false)`). Decide: fix or ship as known.

## 2. Release build only (R8)

- [ ] Mavericks screens open (#200, factory keep rules).
- [ ] Fresh install: activity log works, no SQLite race (#200).
- [ ] Shadow Binder hidden packages hide from a client app (#199).
- [ ] Root compatibility shows descriptions, root level, Shizuku-native label and SU-path hints
      (new serializer keeps in `database/consumer-rules.pro`; an empty list means R8 stripped them).
- [ ] rish works (`rish -c id`): #626 VerifyError fix `ef092a45`.
- [ ] Crash-reporting consent prompt appears (debug builds have no DSN, so only testable here).

## 3. Features (P1, device)

- [ ] **Pairing flow** end to end (never tested since `07e930e3`): notification heads-up per new port,
      reverts to "searching" when the dialog closes, auto-start after pairing.
- [ ] Auto-grant revoke when turned off; Tasker/Locale consent gate.
- [ ] **App database:** App interactions > Data shows "Descriptions and setup tips for 72 apps…".
      Tap → "Checking GitHub…" → result. Before the push it says *failed*; after the push it says
      *already up to date*.
- [ ] Apps list: rows show the description only (no badge); freeze/unfreeze, copy package name and
      no-launcher show snackbars.
- [ ] Snackbar confirmations: Advanced (backup/restore, reset ADB keys), Root integration
      (device-owner toggles), Home restore cards, Device hardening.
- [ ] Floating nav (experimental): no back arrow on Apps / Hub / Settings root only while that tab is
      on the bar; hide a tab → that screen gets its back arrow and no bottom gap; docked default,
      reselect scrolls to top, no autohide under TalkBack.
- [ ] About: Dev channel then Cancel stays Stable; auto-check off disables + clears auto-install;
      missing install permission blocks auto-install with the dialog.
- [ ] Verified installation: VirusTotal key flow + Pithus. Package governor toggle reaches the server.
- [ ] Predictive back in Apps selection and Permission Manager detail (cancel mid-gesture resets).
- [ ] TalkBack: slider step names, badge readout, "Help" long-press action.

## 4. Visual pass (P2, screenshots)

- [ ] One UI corners; Rounded edges off + One UI + a corner override; each corner step incl. System default.
- [ ] Cut style in Permission Manager (Compose) vs an XML screen; the 9 retokenized cards.
- [ ] Bold-only type (onboarding headlineLarge 32sp) and One UI + Bold.
- [ ] Every accent light + dark, Full colour schemes on and off; swatches match the applied accent.
- [ ] High contrast: with an accent, Default, system colours; Android 14 Contrast Medium/High; AMOLED + HC outlines.
- [ ] Switch thumb check/X everywhere (incl. onboarding + GrantPermissions); Expressive button press
      morph; connected groups; LoadingIndicator in app picker / profiles / backup; snackbar inverse
      colours on AMOLED; System Hub containers with an accent.
- [ ] Home card drag lift/drop (re-pick mid-settle), edit-mode in/out, section header double-tap,
      switch-row icon dim/scale; feature badges at a large system font.

## Known, not blocking

- New strings are English-only until translations refresh (Data, Long-press actions, app-database status).
- ~50 toasts remain outside Settings and the Apps list (see `docs/M3E_DESIGN_PLAN.md` 3.4).
- #625 / #629: reports without a stack trace (connection drops; one client binding its user
  service several times a second). Worth a look, not a crash.
- Removed per-app enhancements are tracked in #631–#635.
