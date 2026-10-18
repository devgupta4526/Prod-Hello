# Hello experience direction

## Intended feeling

Warm, confident and human. A place to make a small real-world plan with people, with clear choices and visible consent. The interface should feel carefully composed even when there is no content or the network is unavailable.

The ambition is a distinctive, polished product. “Best” is not a testable completion claim; visual review, accessibility checks, performance measurement and user feedback are the gates.

## Visual system

- Warm ivory canvas, terracotta actions, ink-colored headings and tonal surfaces. Dark mode uses its own semantic containers rather than reusing pale light-mode badges.
- Large, closely spaced headlines paired with short supporting copy. Navigation labels remain literal so expressive headlines do not obscure the screen's purpose.
- Cards use a consistent 24dp shape, restrained borders, generous spacing and minimal shadow. Important actions have their own space instead of competing with identity and metadata.
- Shared-interest chips wrap naturally. Availability always has text as well as color.
- Profile images are real data. Missing images use initials; no fabricated people, popularity metrics or activity.
- Empty-state illustrations are native vector shapes around the relevant feature icon. Empty states explain what will appear and why; network failures have their own retry treatment.
- Message bubbles have rounded directional corners, quieter delivery metadata and a comfortable input surface.

## Responsive behavior

- Feed headers collapse with content scrolling, preserving space on short windows.
- Empty states can scroll at large font sizes.
- Discovery, pools and ticket/event cards use two columns only when the measured viewport provides at least 360dp per card. Font scale above 1.3 uses one column for readability.
- Card identity, badges, prices and actions use separate rows where horizontal competition would clip text.
- Authentication forms scroll and the window accounts for the keyboard. System Back from chat returns through the navigation stack.
- Full tablet navigation rails, two-pane chat and foldable posture handling remain future work; adaptive columns are not equivalent to those features.

## Motion and haptics

| Moment | Behavior | Constraint |
|---|---|---|
| Switch main tab | Existing fade-through transition and a light selection tick | No tick for reselecting the same tab |
| Press a tappable card | Subtle 0.985 scale, 120ms response and selection feedback on activation | Never consume scrolling gestures; noninteractive cards are not focusable buttons |
| Change ticket quantity | Short selection tick | No vibration for initialization or disabled bounds |
| Confirmed action | Visible snackbar plus success feedback | Only after the API reports success |
| Action failure | Visible error plus rejection feedback | Never communicate failure by vibration alone |
| Passive feed failure | Persistent readable retry state | No background error vibration loop |
| Profile photo arrives | 180ms crossfade | Disabled when system animations are disabled |
| Send chat message | Confirmed-send feedback | Preserve draft on failure; no false success on tap |

Haptics use Android's semantic feedback API with no ignore-setting flags or direct vibration patterns. A Profile switch lets the user turn them off in the app; device settings remain authoritative. Physical feel must be checked on actual hardware.

## Accessibility and interaction requirements

- Minimum 48dp interactive targets; principal actions are generally 56dp or taller.
- Color contrast measured from actual tokens; text and icons supplement color.
- Headings identified for accessibility, decorative illustrations excluded from focus, persistent error text announced politely.
- Respect system animation settings; essential state changes never depend on animation.
- Avoid disabled-looking unexplained actions, raw server strings and transient-only errors. Several older feature dialogs still need a dedicated copy/validation pass.
- Preserve navigation context, selected filters and user input. Network cancellation must not let an older tab request replace the latest request's state.

## Verification matrix

| Dimension | Required cases |
|---|---|
| Window | 320dp compact phone, standard phone, landscape, 600dp and 840dp+ windows |
| Appearance | Light and dark; normal, 1.3x and 2x font scales |
| Data | Long names, long translated labels, no image, broken image, no interests, large lists |
| State | Loading, empty, populated, offline/error/retry, permission denied |
| Input | Keyboard visible, TalkBack, touch, back gesture, rotation |
| Motion | Animations enabled/disabled; haptics enabled/disabled both in app and system |
| Performance | Scroll/frame timing on a representative midrange phone; image-load behavior on a slow connection |

## Remaining polish gates

- Complete visual inspection and screenshot evidence for every major screen/state.
- Real-device TalkBack, keyboard, haptic and reduced-motion checks.
- Tablet navigation rail/two-pane designs when the product reaches that scope.
- Dedicated event artwork/content treatment using actual event media, not generic stock imagery in place of data.
- Refine all details sheets and input validation; add reliable pending/disabled states to every destructive or repeatable action.
- Finish the product blockers in FEATURE_BACKLOG.md before treating the interface as release-ready.

Image loading follows the official [Coil Android Views integration](https://coil-kt.github.io/coil/getting_started/) and [network module guidance](https://coil-kt.github.io/coil/network/). It uses a separate image client, not the authenticated API client.

## Status of this delivery

The user requested source-only completion and stopped further build/device work. The final source changes have **not** been rebuilt, linted or run on an emulator/device. No ADB or additional tests were run after that instruction. The earlier verification attempt encountered an APK packaging failure; it is not a passing build result.

The new `ExperienceUiTest` source covers compact cards at 200% font, haptic-preference persistence and labeled sample screenshots. It has not been executed. Physical haptics, rendered screenshots, reduced-motion behavior and end-to-end backend flows remain unverified.

Latest source refinements include bounded 640dp form/account columns, scrollable details sheets, keyboard-focusable actionable cards, tab restoration, and click-only availability writes. Coil is pinned to 3.3.0 for compatibility with the existing Kotlin compiler; upgrading the toolchain is separate work.
