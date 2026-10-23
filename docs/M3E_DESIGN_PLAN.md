# Material 3 Expressive plan for the stable release

Built from a full read of m3.material.io (all 91 pages: foundations, styles, every component's
overview/specs/guidelines/accessibility tabs and token values, and the M3 Expressive posts) and an
audit of the app against it. Platform facts that shape the plan:

- MDC-Android **1.14 is the last Views release** (maintenance mode). Polish what exists in Views;
  build new surfaces in Compose. No wholesale migration.
- Compose Material3 1.5.0-alpha22 (pinned) already has `LargeFlexibleTopAppBar`,
  `AppBarWithSearch`, `HorizontalFloatingToolbar`, `ShortNavigationBar`, `WideNavigationRail`.
- One UI mode / one-handed mode stay: they fit M3E's "component flexibility" tactic. They become
  real flexible app bars, not spacers.

Effort: S = hours, M = a day or two, L = several days.

## Phase 1 - foundations (tokens first, everything else inherits)

| # | Change | Why | Effort |
|---|---|---|---|
| 1.1 | **Motion tokens.** Set the six `motionSpring*` attrs (Expressive overlay, Standard overlay when "Expressive animations" is off). Fix `res/interpolator/m3e_emphasized_decelerate.xml` (it is the standard curve, not 0.05,0.7,0.1,1). One `Motion` helper over `MotionUtils.resolveThemeSpringForce`; `applySpringTouch` on the fast-spatial spring. | Four motion systems today; MDC components use defaults | S |
| 1.2 | Replace timed `Overshoot*`/legacy-curve animators with springs (spatial may overshoot, alpha/colour never). Compose: `MaterialTheme.motionScheme.*Spec()` instead of hand tweens. Durations on the token ladder (50/100/150/200, 250-400, 450-600). "None" intensity means 0. | Interruptible, consistent physics | M |
| 1.3 | **Shape scale.** Add `shapeAppearanceCornerLargeIncreased` (20), `ExtraLargeIncreased` (32), `ExtraExtraLarge` (48) to every shape overlay; snap off-scale radii (6/18/26/36/44) to the scale; move hard-coded `cardCornerRadius`/drawable radii to shape attrs; one Compose `Shapes` per style so XML and Compose match (XL is 32 vs 28 today). | Same card looks different between XML and Compose | M |
| 1.4 | **Type scale.** "Bold typography" = remap to the 15 *Emphasized* styles, not blanket bold. Body back to 400. One UI overlay sizes keep the hierarchy (titleLarge 26 > headlineSmall 24 today). Nothing below 11sp (badges are 9sp). | Hierarchy inversions, illegible small text | M |
| 1.5 | **Colour.** Regenerate the 9 accents as full schemes from seeds (tone-80 dark primaries, matching neutrals, inverse and fixed roles; today only 12 roles change and dark primaries are neon). Remove alpha-faked containers (System Hub). Status/RISKY/ONE UI colours as harmonized custom roles. | Neutrals clash with accents; contrast unpredictable | L |
| 1.6 | **Contrast.** Medium/High contrast overlays + `ColorContrast.applyToActivitiesIfAvailable`; the in-app "High contrast" applies the HC scheme (today it only recolours outlines). | System contrast ignored | M |
| 1.7 | AMOLED/Black: segments, slider track, shape tiles get a role that stays visible on black (known defect). | 1.1:1 contrast | S |

## Phase 2 - components

| # | Change | Effort |
|---|---|---|
| 2.1 | **Switch checkmark is invisible** (`thumbIconTint=?colorOnPrimary` on an on-primary handle). Remove the tint, icon on both states (check / X), one theme-level `materialSwitchStyle` so every switch matches. | S |
| 2.2 | Buttons: retarget direct `Widget.Material3.Button*` to `Widget.Material3Expressive.*` (press/selected shape morph); drop the Full shape overlay in `CardButton`; one filled button per card (Start), others tonal/text, side by side in a `MaterialButtonGroup` instead of stacked. Remove "… button" content descriptions. | M |
| 2.3 | Toggle groups: one pattern (connected group, label-large, 16dp padding, 8dp inner corner, `selectableGroup()`), replacing the outlined update-card group and the custom onboarding/automation groups. | M |
| 2.4 | Progress: legacy `ProgressBar` -> `LoadingIndicator` (<5 s waits) or determinate linear (longer); in buttons tint to the label. | M |
| 2.5 | Settings rows to the Lists spec (56/72/88dp, 16dp side padding, 24dp icon in a 40dp container, body-large label + body-medium supporting text, trailing switch). Compose rows match XML rows (36dp icon and summary-under-control today). | M |
| 2.6 | Sliders only for ordinal values, inactive track `secondary-container`, value applied while dragging (or live preview), TalkBack state description. Categorical choices (accent, shape style) become swatch/shape tile pickers with `selected` semantics. | M |
| 2.7 | Home cards: drop `checkable`, clickable only with a root action, one card type (filled or outlined), no chips on a clickable card. | S |
| 2.8 | Help: no tooltip on labelled rows; visible info affordance; help sheet with `BottomSheetDragHandleView` and a text-button close. Badges readable (11sp+) and in the accessible title. | M |

## Phase 3 - app shell and motion between screens

| # | Change | Effort |
|---|---|---|
| 3.1 | **Transitions:** `AppActivity` overwrites `AppBarActivity`'s shared axis with `Explode` (not an M3 pattern). One system: shared axis X forward/back, fade through for tab switches, container transform card -> screen later. Toolbar back uses `finishAfterTransition()`. Settings sub-pages get shared-axis fragment transitions (jump cuts today). Retest the Android 16 transition crash (#483). | S-M |
| 3.2 | **Nav bar:** labels always on by default, indicator `secondaryContainer`, active label `secondary`, reselect scrolls to top, no autohide with TalkBack, at least 3 tabs, no squish/bounce on the container, docked (square, full width) as the default style with floating as an opt-in look. | S |
| 3.3 | **One app-bar contract:** fill-on-scroll + compress everywhere; `LargeFlexibleTopAppBar`/`MediumFlexibleTopAppBar` in Compose screens (Hub, Device control, Permission manager), headlines wrap instead of ellipsize, no back arrow on top-level destinations, the Apps header collapses (its pinned toolbar blocks the scroll range today), One UI reach height from one shared helper. | M |
| 3.4 | Sheets start at <=50% and pull up; snackbars back to inverse-surface and default shape, toasts that confirm an action become snackbars (about 120 toasts vs 11 snackbars); search to `SearchBar`/`AppBarWithSearch` with predictive back. | M |
| 3.5 | Predictive back progress for Apps selection mode and Compose sub-screens (Home edit mode already does it well). | S-M |

## Phase 4 - after stable

Adaptive navigation (rail on expanded/DeX, list-detail for Settings), single-activity shell with a
persistent nav bar, container transforms for every Home card, Compose-first for new surfaces.

## Writing (runs alongside)

Sentence case (about 185 Title Case strings), no "Are you sure…" titles (state the consequence),
contractions, no periods on one-line messages, exclamation marks only for real celebrations, no
ampersands in body text, consistent "Developer options".
