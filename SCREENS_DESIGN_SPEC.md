# Romear (Hello) — Master UI/UX Element-by-Element Design Specification

This document is the **definitive, pixel-precise element specification** for Romear. Every screen, component, container, text string, icon, radius, padding, hex color, and interactive state is documented below for exact Figma design and Android implementation.

---

## 🎨 Global Design System & Token Foundation

### 1. Color Palette (Dark Mode First)
| Token Name | Hex Code | Usage |
|:---|:---|:---|
| `bg_canvas` | `#0A0D14` | Main screen window background |
| `bg_surface_1` | `#121620` | Elevated cards, list items, input fields |
| `bg_surface_2` | `#1A202C` | Modals, bottom sheets, higher elevation cards |
| `bg_surface_pill` | `#242C3D` | Inactive chips, neutral pill tags, subtle buttons |
| `border_subtle` | `#2A3346` | Card stroke (1dp), divider lines (0.5dp) |
| `border_focus` | `#FF6B4A` | Active input field stroke, selected card outline |
| `primary_coral` | `#FF5C39` | Primary CTAs, active tab icon, brand accent |
| `primary_coral_end`| `#FF7854` | Gradient stop for primary buttons |
| `accent_lavender` | `#8B5CF6` | Daily icebreakers, creative prompt cards |
| `accent_emerald` | `#10B981` | Online status indicator, "Open to meet" chip |
| `accent_amber` | `#F59E0B` | "Browsing" status, expiring countdown badge |
| `text_primary` | `#F8FAFC` | 100% white-slate for titles, headlines, inputs |
| `text_secondary` | `#94A3B8` | 70% muted slate for subtitles, metadata, timestamps |
| `text_tertiary` | `#64748B` | 50% slate for eyebrows, placeholders, captions |
| `scrim_overlay` | `#CC070A0F`| 80% opacity dark scrim for modals and hero photo text readability |

### 2. Typography Hierarchy (Outfit / Plus Jakarta Sans)
| Style Name | Size | Weight | Line Height | Letter Spacing | Color |
|:---|:---|:---|:---|:---|:---|
| `Display / Hero` | `32sp` | Bold (700) | `38sp` | `-0.5px` | `text_primary` |
| `Screen Title` | `24sp` | SemiBold (600) | `30sp` | `-0.3px` | `text_primary` |
| `Section Title` | `18sp` | SemiBold (600) | `24sp` | `0px` | `text_primary` |
| `Card Headline` | `16sp` | Bold (700) | `22sp` | `0px` | `text_primary` |
| `Body Large` | `15sp` | Regular (400) | `22sp` | `0.1px` | `text_primary` |
| `Body Standard` | `14sp` | Regular (400) | `20sp` | `0px` | `text_secondary` |
| `Body Bold / CTA`| `14sp` | SemiBold (600) | `20sp` | `0.2px` | `text_primary` |
| `Eyebrow / Badge`| `11sp` | Bold (700) | `14sp` | `1.2px` (Caps) | `primary_coral` / `accent_lavender` |
| `Micro / Caption`| `11sp` | Medium (500) | `14sp` | `0px` | `text_tertiary` |

### 3. Surface Radii & Elevations
- **Full Pill**: `999dp` (Buttons, status tags, search chips)
- **Bottom Sheet Top Corners**: `28dp`
- **Standard Card**: `22dp`
- **Nested Card / Prompt Quote**: `16dp`
- **Input Fields**: `16dp`
- **Avatar Badges**: `50%` (Circle)

---

## 📱 Detailed Element-by-Element Screen Specifications

---

### GROUP 1: Authentication, Onboarding & Trust (12 Screens)

---

#### SCREEN 1.1: Splash & Brand Launch Screen
- **Canvas**: Background `#0A0D14`, full bleed edge-to-edge.
- **UI Elements**:
  1. `brand_monogram_container`: Center aligned, `88dp x 88dp`, radius `26dp`, background `primary_coral` gradient with ambient soft blur glow (`#FF5C39` at 30% alpha, blur radius `32dp`).
  2. `brand_monogram_icon`: Center of container, `44dp x 44dp`, vector path of stylized double-ring "H" wave, tint `#FFFFFF`.
  3. `brand_tagline`: `14sp` Medium, color `#94A3B8`, text: `"A hello starts here."`, margin top `24dp`.
  4. `launch_progress_spinner`: `24dp x 24dp` bottom-docked (`margin_bottom: 48dp`), circular indeterminate stroke `2dp`, color `primary_coral`.

---

#### SCREEN 1.2: Welcome & Value Walkthrough Carousel
- **Canvas**: Background `#0A0D14`, status bar dark transparent.
- **Top Bar**:
  - `brand_logo_small`: Top-left (`padding_left: 24dp`, `top: 20dp`), text `"romear"`, `20sp` Bold, color `text_primary`.
  - `btn_skip`: Top-right, text `"Skip"`, `14sp` Medium, color `text_tertiary`.
- **Carousel Section (`ViewPager2` / Carousel)**:
  - Slide 1:
    - `card_visual_1`: `280dp x 280dp` 3D glass card illustration showing two overlapping radar circles with wave ripples.
    - `slide_title_1`: `30sp` Bold, text: `"Real connections.\nRight around you."`, margin top `32dp`.
    - `slide_desc_1`: `15sp` Regular, color `text_secondary`, text: `"No endless swiping. Connect with people open to meeting or chatting in your immediate radius."`.
  - Slide 2:
    - `card_visual_2`: Illustration of 500m friendly blurred bubble with lock icon.
    - `slide_title_2`: `30sp` Bold, text: `"Privacy first.\nAlways fuzzed."`.
    - `slide_desc_2`: `15sp` Regular, color `text_secondary`, text: `"Your exact coordinates are never shown. Only approximate radius and neighbourhood."`.
  - Slide 3:
    - `card_visual_3`: Graphic of verified badge with audio waveform lines.
    - `slide_title_3`: `30sp` Bold, text: `"Real voices.\nVerified profiles."`.
    - `slide_desc_3`: `15sp` Regular, color `text_secondary`, text: `"15-second voice bios and IRL selfie verification to eliminate bots and catfishes."`.
- **Carousel Indicator**:
  - `page_indicator_container`: 3 dots, active dot `24dp x 6dp` pill `primary_coral`, inactive dots `6dp x 6dp` circles `#2A3346`.
- **Bottom Actions Container**:
  - `btn_get_started`: Full width (margin horizontal `24dp`), height `56dp`, radius `999dp`, background gradient `primary_coral` to `primary_coral_end`, text `"Get Started"`, `16sp` SemiBold `#FFFFFF`, elevation `4dp`.
  - `btn_already_account`: Height `44dp`, transparent background, text `"Already have an account? Log In"`, `14sp` Medium `text_secondary`, margin top `12dp`.
  - `disclaimer_text`: `11sp` Regular `text_tertiary`, text: `"By continuing, you agree to Romear's Terms of Service & Privacy Policy"`, center aligned, margin top `16dp`, margin bottom `24dp`.

---

#### SCREEN 1.3: Phone Authentication Screen
- **Canvas**: Background `#0A0D14`.
- **Top Navigation Bar**:
  - `btn_back`: `40dp x 40dp` circular button, background `#1A202C`, icon `ic_arrow_back` (20dp, tint `#FFFFFF`).
- **Header**:
  - `eyebrow_text`: `11sp` Bold, color `primary_coral`, text: `"STEP 1 OF 3 · VERIFICATION"`.
  - `title_text`: `28sp` Bold, text: `"What's your number?"`, margin top `6dp`.
  - `subtitle_text`: `14sp` Regular, color `text_secondary`, text: `"We'll text you a 6-digit code. We only use this to keep Romear bot-free."`, margin top `8dp`.
- **Input Container (`margin_top: 36dp`)**:
  - `phone_input_card`: Height `60dp`, background `#121620`, border `1dp` solid `#2A3346` (focus state: `#FF6B4A`), radius `18dp`, horizontal layout.
  - `country_code_picker`: Width `84dp`, icon `🇮🇳`, text `"+91"`, `16sp` SemiBold `#FFFFFF`, trailing icon `ic_dropdown` (16dp, tint `#94A3B8`).
  - `divider_v`: Width `1dp`, height `28dp`, background `#2A3346`.
  - `phone_edit_text`: Text `18sp` SemiBold `#FFFFFF`, hint `"98765 43210"`, hint color `text_tertiary`, keyboard type `phone`, max length `10`.
- **Safety Assurance Row (`margin_top: 16dp`)**:
  - `icon_shield`: `16dp x 16dp`, tint `accent_emerald`.
  - `text_privacy`: `12sp` Regular, color `text_secondary`, text: `"Your number will never be shown to anyone."`.
- **Bottom CTA**:
  - `btn_send_otp`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Send Verification Code →"`, `16sp` SemiBold `#FFFFFF`, keyboard-docked.

---

#### SCREEN 1.4: 6-Digit OTP Verification Screen
- **Canvas**: Background `#0A0D14`.
- **Top Navigation Bar**:
  - `btn_back`: Circular back icon button.
- **Header**:
  - `eyebrow_text`: `11sp` Bold, color `primary_coral`, text: `"SECURITY CHECK"`.
  - `title_text`: `28sp` Bold, text: `"Enter the 6-digit code"`.
  - `subtitle_row`: Text: `"Sent to +91 98765 43210"`, with adjacent `btn_edit_number` text `"Edit"`, `13sp` Bold `primary_coral`.
- **OTP Input Grid (`margin_top: 36dp`)**:
  - `otp_boxes_row`: 6 discrete boxes, each `48dp x 56dp`, radius `14dp`, background `#121620`, stroke `1.5dp` `#2A3346` (active focus box: stroke `2dp` `#FF6B4A` + subtle glow), text `22sp` Bold `#FFFFFF`, center aligned.
- **Resend Ticker (`margin_top: 28dp`)**:
  - `ticker_text`: `14sp` Regular, text: `"Didn't get a code? Resend in 0:42"`, color `text_secondary`.
  - `btn_resend_active`: (Enabled at 0:00) text `"Resend Code"`, `14sp` Bold `primary_coral`.
- **Bottom CTA**:
  - `btn_verify`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Verify & Continue"`, `16sp` SemiBold `#FFFFFF`.

---

#### SCREEN 1.5: Core Identity Setup (Name & Age)
- **Header**:
  - `step_progress`: `11sp` Bold, text: `"STEP 2 OF 3 · YOUR IDENTITY"`.
  - `title`: `28sp` Bold, text: `"What's your name & age?"`.
  - `subtitle`: `14sp` Regular, color `text_secondary`, text: `"This is how people nearby will see you."`.
- **First Name Input**:
  - `label_name`: `12sp` SemiBold, color `text_secondary`, text: `"FIRST NAME"`.
  - `input_first_name`: Height `56dp`, radius `16dp`, background `#121620`, stroke `1dp` `#2A3346`, text `16sp` SemiBold `#FFFFFF`, hint `"e.g. Kabir"`.
- **Birthday & Age Selector**:
  - `label_dob`: `12sp` SemiBold, color `text_secondary`, text: `"DATE OF BIRTH"`.
  - `dob_container`: Height `56dp`, radius `16dp`, background `#121620`, stroke `1dp` `#2A3346`, layout with calendar icon, text `"14 Oct 1999"`, and right-docked `age_badge` (`"25 yrs old"`, background `#242C3D`, text `12sp` Bold `primary_coral`).
- **Gender & Pronouns Selector**:
  - `gender_chips_group`: 4 selectable chips: `"Woman"`, `"Man"`, `"Non-binary"`, `"More options…"`.
- **Bottom CTA**:
  - `btn_continue`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Next: Photos & Vibe →"`.

---

#### SCREEN 1.6: Intentions & "What are you looking for?"
- **Header**:
  - `title`: `28sp` Bold, text: `"What brings you here today?"`.
  - `subtitle`: `14sp` Regular, color `text_secondary`, text: `"You can change this anytime or toggle Ghost Mode."`.
- **Intention Cards (Selectable multi/single-choice cards)**:
  1. `card_intent_coffee`:
     - Icon: `☕` (`32dp x 32dp` rounded square `#242C3D`).
     - Title: `16sp` Bold, text: `"Casual Coffee & Chats"`.
     - Subtitle: `13sp` Regular, text: `"Spontaneous catchups, cafe recommendations"`.
     - Checkmark: Right-docked circular checkmark icon when selected.
  2. `card_intent_activities`:
     - Icon: `🏃`, Title: `"Activity & Fitness Buddies"`, Subtitle: `"Running, badminton, cycling, gym sessions"`.
  3. `card_intent_work`:
     - Icon: `💡`, Title: `"Co-working & Startup Chat"`, Subtitle: `"Brainstorming, indie hacking, deep work sprints"`.
  4. `card_intent_food`:
     - Icon: `🍕`, Title: `"Food & Evening Hangouts"`, Subtitle: `"Street food runs, late-night dinners, pub quizzes"`.
  5. `card_intent_dating`:
     - Icon: `✨`, Title: `"Dating & Real Connections"`, Subtitle: `"Open to meaningful romance, no games"`.
- **Bottom CTA**:
  - `btn_save_intent`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Continue"`.

---

#### SCREEN 1.7: Photo Studio Grid (6-Slot Manager)
- **Header**:
  - `title`: `28sp` Bold, text: `"Add your best photos"`.
  - `subtitle`: `14sp` Regular, color `text_secondary`, text: `"Add at least 2 real photos of yourself. First photo is your main discovery card."`.
- **6-Slot Grid Container (`2 columns x 3 rows`)**:
  - `slot_1` (Main Photo - larger `2x2` or prominent badge):
    - Image preview: Center-crop, radius `20dp`, aspect ratio 4:5.
    - `badge_main`: Floating top-left pill, background `primary_coral`, text `"MAIN"`, `10sp` Bold `#FFFFFF`.
    - `btn_delete`: Floating top-right circle `28dp x 28dp`, background `#80000000`, icon `✕`.
  - `slot_2` to `slot_6`:
    - Radius `18dp`, border `1.5dp` dashed `#2A3346` when empty, icon `ic_add_photo` (28dp, tint `#64748B`), text `"+"`.
- **Bottom Helper Note**:
  - `text_guideline`: Icon `ic_shield_check` (16dp, tint `accent_emerald`), text `"No group photos as main photo. Faces must be clear."`, `12sp` Regular `text_secondary`.
- **Bottom CTA**:
  - `btn_continue_photos`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Next: Prompts →"`.

---

#### SCREEN 1.8: Hinge-Style Prompts Builder Screen
- **Header**:
  - `title`: `28sp` Bold, text: `"Pick your conversation starters"`.
  - `subtitle`: `14sp` Regular, text: `"Give people an easy reason to say hello."`.
- **Prompt Cards Stack (Up to 3 prompt cards)**:
  - `prompt_card_1`:
    - Radius `20dp`, background `#121620`, stroke `1dp` `#2A3346`, padding `18dp`.
    - `btn_choose_question`: Text: `"Select a prompt question ▼"`, `14sp` SemiBold `primary_coral`.
    - (Upon selecting): Question text: `14sp` Bold `accent_lavender`, e.g. `"A small thing that brings me joy…"`.
    - `input_answer`: Multi-line text field, `16sp` Regular `#FFFFFF`, hint `"Write your answer here…"`, max 140 chars.
    - `char_counter`: Bottom-right, `11sp` Regular `text_tertiary`, `"42/140"`.
  - `btn_add_another_prompt`: Outlined pill button, icon `ic_plus`, text `"Add another prompt (2/3)"`, height `44dp`.
- **Bottom CTA**:
  - `btn_next_prompts`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Next: Voice Bio →"`.

---

#### SCREEN 1.9: Voice Bio Studio Screen
- **Header**:
  - `title`: `28sp` Bold, text: `"Record your 15s voice bio"`.
  - `subtitle`: `14sp` Regular, color `text_secondary`, text: `"Profiles with voice bios get 4x more replies. Say your name and your favourite weekend plan."`.
- **Audio Waveform Canvas**:
  - `waveform_visualizer`: Height `100dp`, 30 vertical bar lines animating dynamically during recording, color `primary_coral`.
  - `timer_display`: `32sp` Bold, text `"0:00 / 0:15"`, center aligned, margin top `16dp`.
- **Record Action Area**:
  - `btn_record_mic`: `80dp x 80dp` circle, background `primary_coral`, pulsing ring animation when active, icon `ic_mic` (36dp, tint `#FFFFFF`).
  - `text_record_hint`: `13sp` Medium `text_secondary`, text: `"Tap to record · 15 seconds max"`.
- **Post-Recording Preview Controls (Visible after recording)**:
  - `btn_play_preview`: `48dp x 48dp` circle, icon `ic_play_arrow`.
  - `btn_delete_re_record`: Text button `"Delete & Re-record"`, color `#EF4444`.
- **Bottom CTA**:
  - `btn_save_voice`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Save Voice Bio"`.

---

#### SCREEN 1.10: IRL Selfie Liveness Verification Screen
- **Header**:
  - `title`: `28sp` Bold, text: `"Get your ✓ IRL badge"`.
  - `subtitle`: `14sp` Regular, color `text_secondary`, text: `"We verify you are a real person with a quick 3-second selfie pose. This photo will not be on your profile."`.
- **Circular Viewfinder Camera View**:
  - `camera_circle_frame`: `260dp x 260dp` circle, stroke `3dp` glowing `#8B5CF6`, shows front camera live stream.
  - `pose_instruction_pill`: Floating overlay inside circle, background `#CC000000`, text: `"Tilt head slightly to the right ➡️"`, `13sp` SemiBold `#FFFFFF`.
- **Status Indicator**:
  - `liveness_mesh_overlay`: Face detection dots overlaying cheekbones and chin.
- **Bottom Action**:
  - `btn_capture_verify`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"I'm Ready · Take Selfie"`.

---

#### SCREEN 1.11: Location & Privacy Primer Screen
- **Visual Graphic**:
  - `graphic_radar_shield`: `140dp x 140dp`, animated shield with concentric glowing 500m rings, soft emerald & coral tint.
- **Headline**: `28sp` Bold, text: `"Your safety is our core design."`
- **3 Trust Points (`padding: 20dp`)**:
  1. `item_fuzzed`:
     - Icon: `ic_shield_location` (`24dp`, tint `accent_emerald`).
     - Title: `15sp` SemiBold, `"Location is always fuzzed"`.
     - Subtitle: `13sp` Regular, `"We only show general distance (e.g. '~400m away'). Never your exact pin."`.
  2. `item_ghost`:
     - Icon: `ic_ghost` (`24dp`, tint `accent_lavender`).
     - Title: `15sp` SemiBold, `"One-tap Ghost Mode"`.
     - Subtitle: `13sp` Regular, `"Go completely invisible whenever you want with zero trace."`.
  3. `item_hidden_zones`:
     - Icon: `ic_home_work` (`24dp`, tint `primary_coral`).
     - Title: `15sp` SemiBold, `"Hidden Zones"`.
     - Subtitle: `13sp` Regular, `"Block out your home or office neighborhood from discovery."`.
- **Bottom Actions**:
  - `btn_enable_location`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Enable Fuzzed Location"`.
  - `btn_learn_more`: Text `"How our location fuzzing works"`, `13sp` Medium `text_secondary`.

---

#### SCREEN 1.12: Push Notification Primer Screen
- **Visual Graphic**: Floating card simulation showing a push notification banner:
  - Avatar: Priya, `"Priya sent you a wave!"`, `"Hey! Loved your Bangalore coffee prompt ☕"`.
- **Headline**: `28sp` Bold, text: `"Don't miss a hello."`
- **Body**: `14sp` Regular `text_secondary`, text: `"Incoming waves expire in 24 hours. Turn on alerts so spontaneous connections don't pass you by."`
- **Actions**:
  - `btn_allow_notifications`: Height `56dp`, radius `999dp`, background `primary_coral`, text `"Turn On Notifications"`.
  - `btn_maybe_later`: Height `44dp`, text `"Maybe Later"`, `14sp` Medium `text_tertiary`.

---

### GROUP 2: Main Discovery & Social Feed (8 Screens & Modals)

---

#### SCREEN 2.1: Discover / Home Feed (★ HERO SCREEN)
- **CoordinatorLayout / AppBarLayout (`bg_canvas: #0A0D14`)**:
- **1. Header Row (`padding_horizontal: 20dp, top: 16dp`)**:
  - `eyebrow_location`: `11sp` Bold, letter spacing `1.2px`, color `primary_coral`, text: `"BANGALORE CENTRAL · MAKE A CONNECTION"`.
  - `title_hello`: `26sp` Bold, text: `"A hello starts here."`.
  - `radar_badge`: Right-docked, `38dp x 38dp`, background `#242C3D`, radius `12dp`, icon `ic_discover` (20dp, tint `primary_coral`), red live pulse dot on corner.
- **2. Instagram Notes / "Live Vibe" Strip (`RecyclerView`, horizontal, height `96dp`)**:
  - **Item 0 (`Self Note / Post Vibe`)**:
    - `avatar_container`: `56dp x 56dp`, circle, border `2dp` dashed `primary_coral`.
    - `icon_plus`: Center `+` badge, background `primary_coral`.
    - `speech_bubble`: Floating above avatar, background `#1A202C`, stroke `1dp` `#2A3346`, radius `12dp`, text: `"⚡ Share vibe"`, `11sp` Bold `#FFFFFF`.
    - `label`: Below avatar, `"Your Note"`, `11sp` Medium `text_tertiary`.
  - **Item 1..N (`Nearby User Vibe Notes`)**:
    - `avatar`: `56dp x 56dp`, circle photo, online green indicator dot (`10dp`) at 4 o'clock position.
    - `speech_bubble`: Floating above, background `#1A202C`, stroke `1dp` `#2A3346`, radius `12dp`, text: e.g. `"☕ Third Wave Roastery"`, `11sp` SemiBold `#FFFFFF`.
    - `label`: Below avatar, User First Name, `11sp` Medium `text_secondary`.
    - `click_action`: Tapping bubble opens Screen 2.7 (Contextual Reply to Note).
- **3. Daily 24h Bangalore Icebreaker Card (`margin_horizontal: 20dp, margin_top: 10dp`)**:
  - `container`: Radius `18dp`, background gradient `#1A1D28` with subtle purple ambient glow, stroke `1dp` `#3B3558`, padding `14dp x 12dp`.
  - `icon_sparkles`: `32dp x 32dp` rounded square, background `#31234F`, text `"✨"`, `16sp`.
  - `text_col`:
    - `eyebrow_time`: `10sp` Bold, color `accent_lavender`, text: `"⚡ DAILY ICEBREAKER · 7 PM"`.
    - `question_text`: `13sp` Bold `#FFFFFF`, text: `"Best late-night food spot in the city?"`.
  - `btn_answer`: Right-docked, `12sp` Bold `primary_coral`, text: `"Answer →"`.
  - `click_action`: Opens Screen 2.3 (Icebreaker Sheet).
- **4. Quick Action Filter & Mode Pills (`margin_horizontal: 20dp, margin_top: 10dp`)**:
  - Horizontal chip group:
    - `chip_open`: Checked by default, radius `999dp`, background `#121620`, stroke `1dp` `#10B981`, text: `"🟢 Open to meet"`, `12sp` SemiBold `#FFFFFF`.
    - `chip_browsing`: Radius `999dp`, background `#121620`, stroke `1dp` `#2A3346`, text: `"🟡 Browsing"`, `12sp` Medium `text_secondary`.
    - `chip_ghost`: Radius `999dp`, background `#121620`, stroke `1dp` `#2A3346`, text: `"👻 Ghost Mode"`, `12sp` Medium `text_secondary`.
- **5. Hero User Cards Feed (`RecyclerView`, adaptive vertical staggered/list, padding bottom `88dp`)**:
  - **Individual Card Component (`item_discovery_user.xml`)**:
    - `card_frame`: Margin horizontal `16dp`, margin bottom `20dp`, radius `24dp`, elevation `6dp`, background `#121620`, stroke `1dp` `#2A3346`.
    - `hero_photo_container`: Relative layout, height `380dp`.
    - `photo_image`: Full width, scaleType `centerCrop`, loaded via Coil.
    - `scrim_gradient`: Bottom-up dark gradient (`#00000000` to `#E60A0D14`, height `160dp`) to ensure text readability.
    - `top_badges_row`: Floating inside photo at top:
      - `distance_pill`: Top-left, background `#CC0E1117`, radius `999dp`, padding `8dp x 4dp`, icon `ic_location` (12dp), text: `"📍 ~400m away"`, `11sp` Bold `#FFFFFF`.
      - `irl_badge`: Next to distance, background `#2563EB`, radius `999dp`, padding `6dp x 3dp`, icon `✓`, text: `"IRL VERIFIED"`, `10sp` Bold `#FFFFFF`.
      - `btn_more`: Top-right, `32dp x 32dp` circle `#99000000`, icon `ic_more_vert`.
    - `user_info_overlay`: Positioned at bottom of photo:
      - `name_age_row`: Text: `"Priya Nair, 24"`, `24sp` Bold `#FFFFFF`.
      - `occupation_subtext`: Text: `"Product Designer · Indiranagar"`, `13sp` Medium `text_secondary`.
    - `up_for_intention_card`:
      - Inner card below photo, margin `14dp`, background `#1A202C`, radius `16dp`, stroke `1dp` `#2A3346`, padding `12dp`.
      - `up_for_tag`: `10sp` Bold `primary_coral`, text: `"⚡ UP FOR TODAY"`.
      - `up_for_content`: `14sp` SemiBold `#FFFFFF`, text: `"Specialty filter coffee & indie music chat"`.
      - `btn_context_reply_vibe`: Right icon `💬 Reply`.
    - `shared_interests_row`:
      - Chips: `#Coffee`, `#Startups`, `#IndieRock`, `#Architecture`.
    - `card_actions_bar`:
      - `btn_like_photo`: `48dp x 48dp` circle, background `#1A202C`, icon `ic_heart` (22dp, tint `#FF5C39`).
      - `btn_say_hello`: Weight 1, height `48dp`, radius `999dp`, background gradient `primary_coral`, icon `ic_hand_wave`, text: `"Say Hello 👋"`, `14sp` SemiBold `#FFFFFF`.

---

#### SCREEN 2.2: Post Live Note / "Share Vibe" Modal
- **Bottom Sheet (`bg_surface_2: #1A202C, radius: 28dp top`)**:
- **Drag Handle**: Center, `36dp x 4dp` pill `#374151`.
- **Title**: `18sp` Bold, text: `"Share your vibe for the next 24h"`.
- **Speech Bubble Editor**:
  - `avatar`: `48dp x 48dp` circle of user.
  - `input_bubble`: Background `#242C3D`, stroke `1.5dp` `primary_coral`, radius `18dp`, padding `14dp`, hint `"What are you up to right now?"`, text `15sp` SemiBold `#FFFFFF`, max chars `60`.
  - `char_counter`: Bottom-right `"24/60"`.
- **Venue Tag Selector**:
  - Horizontal list of suggested nearby spots: `"☕ Third Wave Coffee"`, `"💻 WeWork Galaxy"`, `"🌳 Cubbon Park"`, `"+ Custom location"`.
- **Publish Action**:
  - `btn_publish_note`: Height `52dp`, radius `999dp`, background `primary_coral`, text `"Share Vibe ⚡"`.

---

#### SCREEN 2.3: Daily Bangalore Icebreaker Discussion Sheet
- **Canvas / Sheet**: Height 80% screen, background `#121620`.
- **Header**:
  - `badge_topic`: `11sp` Bold `accent_lavender`, `"TODAY'S BANGALORE ICEBREAKER"`.
  - `question`: `20sp` Bold `#FFFFFF`, `"What's the best late-night food spot in the city?"`.
  - `subtext`: `13sp` Regular `text_secondary`, `"24 nearby people answered"`.
- **Your Answer Input Box**:
  - Height `52dp`, radius `16dp`, background `#1A202C`, input text, right-docked `btn_post_answer` (`"Post"`).
- **Responses List (`RecyclerView`)**:
  - Each item:
    - `avatar`: `36dp x 36dp`, First Name, Distance (`"800m"`).
    - `answer_quote`: Background `#1A202C`, radius `14dp`, padding `12dp`, text: e.g. `"Empire Church Street at 2 AM for Ghee Rice and Chicken Kebab!"`, `14sp` Regular.
    - `btn_reply_wave`: Right-docked `👋 Say Hello`, opens Screen 2.7 pre-filled with this response.

---

#### SCREEN 2.4: Discovery Filter & Preferences Sheet
- **Bottom Sheet**: Height 70% screen, radius `28dp top`.
- **Header**: Title `"Discovery Preferences"`, right-docked `"Reset"`.
- **Filter Sliders**:
  1. `Distance Slider`:
     - Label: `"MAXIMUM DISTANCE"`, right value: `"5 km"`.
     - Slider track: `primary_coral`, range `500m` to `25km`.
  2. `Age Range Slider`:
     - Label: `"AGE RANGE"`, right value: `"21 - 30"`.
     - RangeSlider: 2 thumbs, track `primary_coral`.
  3. `Verified Profiles Only`:
     - Switch row: `"Only show ✓ IRL Verified people"`, toggle switch `primary_coral`.
  4. `Intentions Filter`:
     - Multi-select chips: `"Coffee"`, `"Activities"`, `"Work"`, `"Food"`, `"Dating"`.
- **Bottom Action**:
  - `btn_apply_filters`: Height `52dp`, radius `999dp`, background `primary_coral`, text `"Apply Filters (18 people nearby)"`.

---

#### SCREEN 2.5: Radar Hotspots & Map View
- **Canvas**: Map view styled with dark vector tiles (`#0A0D14` land, `#141923` roads, `#1E2533` water).
- **Overlays**:
  - Concentric glowing circles representing public hotspots (e.g. Indiranagar 12th Main, Church Street, Koramangala 4th Block).
  - Inside each circle: Category icon (`☕`, `💻`, `🍻`) + Pill badge with live people count (`"14 open"`).
- **Bottom Sliding Carousel**:
  - Tapping a hotspot slides up a horizontal card deck of users currently open at that hub.

---

#### SCREEN 2.6: Full User Profile Bottom Sheet
- **Modal Bottom Sheet**: Full height (92% screen), drag handle at top.
- **Hero Image Carousel**:
  - Full-width swipeable photos (height `340dp`).
  - Top progress segment bars (like Instagram stories: 3 white bars indicating active photo).
  - Floating `btn_close` (top-left) and `btn_safety_shield` (top-right).
- **Profile Header Overlay**:
  - `name_age`: `"Priya Nair, 24"`, `26sp` Bold.
  - `badges_row`: `✓ IRL Verified` badge, `📍 ~400m away` pill.
  - `job_location`: `"Product Designer @ FinTech · Indiranagar"`.
- **Voice Bio Card (`margin: 16dp`)**:
  - Radius `18dp`, background `#1A202C`, stroke `1dp` `#2A3346`, padding `14dp`.
  - `btn_play_audio`: `44dp x 44dp` circle, background `primary_coral`, icon `ic_play_arrow`.
  - `waveform_bars`: Animated visualizer bars representing 15s audio.
  - `voice_label`: `12sp` Bold, `"15s Voice Intro · Tap to listen 🎙️"`.
- **Hinge Prompt Cards (3 Quote Cards)**:
  - Each card: Radius `18dp`, background `#121620`, stroke `1dp` `#2A3346`, padding `16dp`, margin bottom `12dp`.
  - `prompt_question`: `12sp` Bold `primary_coral`, e.g. `"A small thing that brings me joy…"`.
  - `prompt_answer`: `16sp` Medium `#FFFFFF`, line height `24sp`, e.g. `"Finding a quiet corner at a specialty cafe with a sketchbook and a flat white."`.
  - `btn_reply_prompt`: Tonal pill button, `"Reply to this prompt 💬"`, height `38dp`.
- **Shared Interests Cloud**:
  - Flexbox chip container: `#CoffeeEnthusiast`, `#Figma`, `#IndieRock`, `#Badminton`.
- **Sticky Bottom Action Bar**:
  - Height `76dp`, background `#CC0A0D14` with backdrop blur.
  - `btn_sticky_hello`: Full width, height `52dp`, radius `999dp`, background gradient `primary_coral`, icon `ic_hand_wave`, text: `"Say Hello to Priya 👋"`.

---

#### SCREEN 2.7: Contextual "Say Hello" Dialog
- **Modal Dialog**: Centered, width 90% screen, radius `24dp`, background `#1A202C`, stroke `1dp` `#2A3346`.
- **Header Row**:
  - `avatar`: `40dp x 40dp` circle of recipient.
  - `title`: `16sp` Bold, `"Say Hello to Priya"`.
  - `btn_dismiss`: Top-right `✕`.
- **Context Preview Card (When triggered from a prompt/photo/note)**:
  - Radius `12dp`, background `#121620`, padding `10dp`, stroke `1dp` `#2A3346`.
  - `context_label`: `10sp` Bold `primary_coral`, `"REPLYING TO PHOTO"`.
  - `context_snippet`: `13sp` Medium `text_secondary`, `"Photo #2 · Third Wave Coffee"`.
- **Message Input Area**:
  - `input_hello_text`: Height `90dp`, radius `14dp`, background `#121620`, stroke `1dp` `#2A3346`, hint `"Add a thoughtful note… (no generic 'hey')"`, text `14sp` Regular `#FFFFFF`.
  - `char_count`: Bottom-right, `"48/140"`.
- **Quick Suggested Starters (Tappable chips)**:
  - `"Love that cafe! ☕"`, `"Up for a game of badminton? 🏸"`, `"I work in tech too! 💡"`.
- **Actions Bar**:
  - `btn_cancel`: Text `"Cancel"`, color `text_tertiary`.
  - `btn_send`: Height `46dp`, radius `999dp`, background `primary_coral`, text `"Send Wave 🚀"`, `14sp` SemiBold `#FFFFFF`.

---

#### SCREEN 2.8: Empty Discovery / Radar Expansion Screen
- **Canvas**: Centered vertically in main feed.
- **Graphic**: `140dp x 140dp` radar rings illustration with glowing center pulse.
- **Headline**: `22sp` Bold, `"No one nearby right now"`.
- **Body**: `14sp` Regular `text_secondary`, line height `20sp`, `"People open to saying hello will appear here as they come online in your area."`.
- **Action Buttons**:
  - `btn_expand_radius`: Height `48dp`, radius `999dp`, background `primary_coral`, text `"Expand Radius to 10 km"`.
  - `btn_invite`: Height `44dp`, transparent, text `"Share Romear with Friends"`, `14sp` SemiBold `primary_coral`.

---

### GROUP 3: Happening, Circles & Social Hangouts (4 Screens)

---

#### SCREEN 3.1: Happening Feed
- **Header**:
  - Eyebrow: `SPONTANEOUS HANGOUTS`
  - Title: `Happening Today.`
  - `btn_create_hangout`: Top-right pill button, background `primary_coral`, text `"+ Host"`.
- **Category Filter Pills**:
  - `All`, `☕ Coffee`, `🏃 Sports`, `🍕 Food`, `🎮 Tech & Games`.
- **Hangout Cards List (`RecyclerView`)**:
  - **Hangout Card Element**:
    - Radius `22dp`, background `#121620`, stroke `1dp` `#2A3346`, padding `16dp`, margin bottom `16dp`.
    - `top_row`:
      - Category badge: Background `#242C3D`, text: `"🏃 SPORTS & BADMINTON"`, `10sp` Bold.
      - Countdown pill: Right-docked, background `#372814`, stroke `1dp` `#F59E0B`, text: `"Starts in 45m"`, `11sp` Bold `#F59E0B`.
    - `title`: `18sp` Bold `#FFFFFF`, `"Evening Badminton at Play Arena"`.
    - `venue_row`: Icon `ic_location` (14dp), text: `"Play Arena, Sarjapur Road · 1.2 km away"`, `13sp` Regular `text_secondary`.
    - `host_attendees_row`:
      - Stacked overlapping avatars (3 avatars, `32dp x 32dp`).
      - Text: `"Host: Rahul + 2 joined · 2 spots left"`, `12sp` Medium `text_secondary`.
    - `btn_join_hangout`: Full width, height `42dp`, radius `999dp`, background `#242C3D`, text: `"Join Hangout →"`, `13sp` SemiBold `#FFFFFF`.

---

#### SCREEN 3.2: Hangout Details Bottom Sheet
- **Full Bottom Sheet (`bg_surface_2`)**:
- **Hero Venue Banner**: Height `180dp`, photo of venue with rounded top corners.
- **Hangout Metadata**:
  - Title: `22sp` Bold, `"Evening Badminton at Play Arena"`.
  - Time & Date: `"Today, 6:30 PM – 8:00 PM"`.
  - Public Meeting Point: `"Court 4, Reception Area"`.
- **Host Card**:
  - Avatar, Name, `✓ IRL Verified`, bio: `"Inter-college player, friendly matches only!"`.
- **Attendee Roster**:
  - List of joined members with their profile previews and shared interests.
- **Group Vibe Guidelines**:
  - Bullet points: `"Beginners & intermediates welcome"`, `"Rackets available on rent"`.
- **Sticky CTA**:
  - `btn_rsvp`: Height `52dp`, radius `999dp`, background `primary_coral`, text `"RSVP & Join Group Chat"`.

---

#### SCREEN 3.3: Create / Host a Hangout Modal
- **Form Modal**:
  - `title_input`: Label `"WHAT'S THE PLAN?"`, hint `"e.g. Saturday Morning Filter Coffee"`.
  - `category_grid`: 6 category buttons with icons.
  - `venue_search`: Search box with Google Places auto-complete for Bangalore cafes/venues.
  - `time_picker`: Chips: `"In 1 hour"`, `"Tonight 7 PM"`, `"Tomorrow Morning"`, `"Custom…"`.
  - `max_people_slider`: Range 2 to 8 people (keeps groups intimate).
  - `btn_publish`: Height `52dp`, radius `999dp`, background `primary_coral`, text `"Publish Hangout to Nearby Radar"`.

---

#### SCREEN 3.4: Host Management Dashboard
- **Components**:
  - `pending_requests_section`: Cards of users requesting to join with `Approve ✓` and `Decline ✕` buttons.
  - `accepted_members_list`: Current attendees with remove options.
  - `btn_broadcast`: Send an announcement notification to all attendees (e.g. *"I'm wearing a yellow jacket by the entrance"*).
  - `btn_cancel_hangout`: Destructive red text button.

---

### GROUP 4: Inbox, Matches & Waves (3 Screens)

---

#### SCREEN 4.1: Inbox Main Hub
- **Header**:
  - Title: `Inbox.`
  - Unread badge counter.
- **Section 1: Incoming Waves / Pending Hellos (Deck Carousel)**:
  - Horizontal card deck (each card `240dp x 160dp`, radius `20dp`, background `#1A202C`, stroke `1dp` `#2A3346`).
  - Top timer badge: `10sp` Bold `#F59E0B`, `"⏳ EXPIRES IN 18H"`.
  - Sender avatar & name: Priya, 24.
  - Attached starter note: `"Hey! CTR Benne Dosa is unmatched 🤤"`.
  - Actions: `Decline ✕` (circle button `#242C3D`), `Accept & Chat 👋` (coral pill button).
- **Section 2: Active Conversations (`RecyclerView`)**:
  - **Chat Thread Item**:
    - Avatar (`52dp x 52dp`), online green dot indicator.
    - Title: Name, `16sp` Bold `#FFFFFF`.
    - Message Preview: `14sp` Regular `text_secondary`, truncate 1 line.
    - Timestamp: Top-right, `"2m ago"`, `12sp` Regular `text_tertiary`.
    - Unread bubble: Coral circle with digit (e.g. `1`), `11sp` Bold `#FFFFFF`.

---

#### SCREEN 4.2: Incoming Wave Review Card
- **Detailed Sheet**:
  - Full-screen card view of the person who sent the hello, displaying their photo, the exact prompt/photo they reacted to, their custom note, and 2 large bottom buttons: `Pass ✕` and `Accept & Start Chatting 👋`.

---

#### SCREEN 4.3: Match & Wave Celebration Overlay
- **Full Screen Modal with Dark Frost Blur**:
- **Animation**: Interlocking glowing coral & lavender rings with floating particle confetti.
- **Headline**: `30sp` Bold, text: `"It's a Hello! 🎉"`.
- **Subtitle**: `15sp` Regular `text_secondary`, text: `"You and Kabir are now connected. Conversations spark when you reply within 24h."`.
- **Actions**:
  - `btn_open_chat`: Height `52dp`, radius `999dp`, background `primary_coral`, text `"Send a Message"`.
  - `btn_keep_browsing`: Transparent text `"Keep Browsing"`.

---

### GROUP 5: Direct Messaging & Real-World Meetup Tools (3 Screens)

---

#### SCREEN 5.1: Active 1-on-1 Chat Screen
- **Top Navigation Bar**:
  - `btn_back`: Circular arrow button.
  - `avatar`: `40dp x 40dp` circle.
  - `name_status_col`: Name `"Priya Nair"`, status `"Online · ~400m away"`.
  - `btn_safety_menu`: Top-right shield icon (opens Safety & Report sheet).
- **Top Sticky Context Anchor**:
  - Background `#1A202C`, padding `8dp x 12dp`, icon `ic_bolt`, text: `"Connected via: Bangalore Coffee Icebreaker"`, `12sp` SemiBold `primary_coral`.
- **Message Stream (`RecyclerView`)**:
  - **Receiver Bubble (Priya)**:
    - Left-aligned, radius `18dp 18dp 18dp 4dp`, background `#1A202C`, stroke `1dp` `#2A3346`, padding `12dp x 14dp`.
    - Text: `15sp` Regular `#FFFFFF`, line height `21sp`.
    - Timestamp: Bottom-right inside bubble, `10sp` `text_tertiary`.
  - **Sender Bubble (You)**:
    - Right-aligned, radius `18dp 18dp 4dp 18dp`, background gradient `primary_coral` to `primary_coral_end`, padding `12dp x 14dp`.
    - Text: `15sp` Regular `#FFFFFF`.
    - Status checkmarks: `✓✓` blue double-tick for read receipt.
  - **Interactive Double-Tap Reaction Pill**:
    - Floating at bottom corner of bubble: `❤️ 1` or `👋 1`.
  - **Voice Note Bubble**:
    - Play button, scrubber waveform, duration `"0:14"`.
- **Bottom Message Bar**:
  - `btn_lets_meet`: Pill button or icon `🗓️`, tooltip `"Suggest Meetup"`.
  - `btn_mic`: Audio note recorder button.
  - `input_message`: Height `44dp` expanding to `100dp`, radius `22dp`, background `#121620`, hint `"Type a message…"`.
  - `btn_send`: Circle `40dp x 40dp`, background `primary_coral`, icon `ic_send`.

---

#### SCREEN 5.2: In-Chat "Let's Meet" Safe Meetup Planner Sheet
- **Purpose**: One-tap tool to convert digital chat into a safe real-world hangout.
- **Components**:
  - Title: `20sp` Bold, `"Plan a Safe Public Meetup"`.
  - `curated_cafes_picker`: Suggested verified cafes nearby (e.g., *"Third Wave Coffee, Indiranagar"*).
  - `date_time_picker`: Chips: `"Tomorrow 5:00 PM"`, `"Saturday 11:00 AM"`.
  - `btn_send_meetup_invite`: Sends interactive **Meetup Card** into chat stream:
    - Embedded card in chat with map thumbnail, venue name, date/time, and `Accept Invite ✓` / `Suggest Different Time` buttons.

---

#### SCREEN 5.3: Full-Screen Media Lightbox
- **Canvas**: Pure black `#000000`.
- **Components**:
  - Pinch-to-zoom high-res photo viewer.
  - `btn_close`: Top-left circle.
  - Metadata overlay: Photo caption, date uploaded.

---

### GROUP 6: Tickets, Passes & Local Experiences (1 Screen)

---

#### SCREEN 6.1: Tickets & Mixers Pass Screen
- **Tab 1: Upcoming Events Feed**:
  - Featured banner card: Hero artwork, Title `"Indiranagar Founders & Creatives Mixer"`, Date `"Friday, 7:30 PM"`, Venue `"The Humming Tree"`, Price `"₹499 (Includes beverage)"`.
- **Tab 2: My Tickets Wallet**:
  - Apple Wallet-style dark glass pass with perforated ticket edge graphics.
  - Event title, countdown timer, entry seat/tier.
  - Dynamic QR code for door scanning.
  - `btn_add_to_wallet`: Button to export to Google Wallet.

---

### GROUP 7: My Profile, Privacy & Safety Center (1 Screen)

---

#### SCREEN 7.1: My Profile, Privacy & Safety Center
- **Header**:
  - User avatar (`80dp x 80dp`), Name, Age, `✓ IRL Verified` badge.
  - `btn_edit_profile`: Outlined pill button, `"Edit Profile & Prompts"`.
  - `preview_mode_toggle`: Switch: `"Preview how others see you"`.
- **Voice Bio Preview & Editor**:
  - Audio waveform player with `"Re-record 15s intro"` button.
- **Privacy & Safety Section**:
  - `Ghost Mode Switch`: Instant toggle to go completely off the radar.
  - `Fuzzing Radius Selector`: Segmented control (`300m` • `500m` • `1km`).
  - `Hidden Zones Manager`: Add home/office address to hide discovery within 500m.
  - `Blocked Contacts`: List of blocked users with unblock actions.
- **Account Settings**:
  - Phone number, notifications, community guidelines, logout, delete account.

---

### GROUP 8: Safety, Moderation & Reporting Overlays (3 Overlays)

---

#### SCREEN 8.1: Report User Dialog
- **Modal Dialog**:
  - Title: `18sp` Bold, `"Report User"`.
  - Reason Selector (Radio group):
    - `Spam or commercial promotion`
    - `Harassment or inappropriate language`
    - `Fake profile or catfish`
    - `Under 18 years old`
    - `Other`
  - Additional Notes input field (optional).
  - `btn_submit_report`: Coral button.

#### SCREEN 8.2: Block User Confirmation Modal
- **Destructive Alert Dialog**:
  - Title: `"Block Kabir?"`.
  - Message: `"They will not be able to see your profile, send you hellos, or see you on the nearby radar. Your chat history will be deleted."`.
  - Actions: `Cancel` (neutral) and `Block User` (destructive red).

#### SCREEN 8.3: IRL Meetup Safety Checklist Sheet
- **BottomSheet**:
  - Title: `"Real-World Meetup Safety"`.
  - 3 Safety Checkpoints:
    1. Always meet in a busy, public cafe or venue.
    2. Share your live location with a friend or family member.
    3. Keep your transportation independent.
  - `btn_got_it`: `"I Understand · Proceed"`.
