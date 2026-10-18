# Design system source — Phase 1 [~]

Kotlin/XML Material 3 components; ViewBinding; English string resources. Palette resources
have light and dark variants. Layouts use scroll containers, wrap-content text and 48dp
minimum touch targets. Availability uses icons/text as well as color. Motion checks system
animation settings. The debug source set includes DesignGalleryActivity; release excludes it.

The blueprint's orange is retained as an accent. Primary button color is darkened to
#B84020 for white-text contrast; it must still receive the required contrast audit.
Spacing: 4/8/16/24/32/48dp. Large titles use system sans-serif medium, avoiding an unlicensed
font asset. Typography supports font scaling; no runtime accessibility result is claimed.

Written components: primary/secondary/disabled buttons, fields, OTP cells, interest chips,
availability controls, cards, empty/error/offline/loading states and navigation. Remaining
catalog items tied to later product phases (map pins, messages, tickets, scanner overlays)
are not claimed complete. Material shared-axis/container transitions and full gallery
coverage remain to be completed before Phase 1 exit.

Pending verification after the user's code-only pass: contrast, 200% font scale, TalkBack,
remove-animations, small-screen layouts, landscape and dark-mode visual review.
