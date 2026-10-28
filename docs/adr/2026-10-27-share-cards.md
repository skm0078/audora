# ADR 2026-10-27-share-cards: quote cards made on the phone, with the credit and the ListenLock mark always on

**Status:** Accepted (2026-10-27). First of the three features the maintainer moved ahead of v0.1.0 (share cards,
video export, page-flip reader; plan "Share cards, video and page flips: plan", mockups on the design canvas).

## Context

Sharing is the community edition's main way to spread: a quote that looks good in WhatsApp Status or a Story
carries the book, the author and the app. The app could already share a passage as audio (plan item 41) or as plain
text, but not as a picture. Everything must stay on the phone, cost little, and keep shares short and credited.

## Decision

- **One Share row** in the sentence menu opens its choices in the same dialog: As audio (unchanged), As a picture,
  As text. The old "Share text" and "Share as audio" rows move inside it, so the menu is one row shorter.
- **Quote rule** (`core/share/CardQuote`, `ShareCardConfig`): at most **60 words**; a longer selection is cut at a
  sentence end (., !, ?, ।, ॥) when one keeps at least half, else after the 60th word with "…". The card says when it
  was shortened. The book's own quote marks around the whole quote are dropped (the card draws its own).
- **Credit always on**: title, author and page are part of the card layout and cannot be turned off. The page label is
  in the quote's language. An optional dedication ("For Ma", 40 characters) is printed under it and saved nowhere.
- **ListenLock mark always on** (maintainer's rule): drawn by `ShareCardFrame` itself, not by each card, so no card,
  including the stats and video cards that will reuse the frame, can leave it out.
- **Rendering**: the card is ordinary Compose, laid out on a 360-dp-wide canvas at the picture's full size
  (1080 x 1080, 1080 x 1920, 1080 x 1350) with its own density and font scale 1, shown scaled to fit, and recorded
  into a `GraphicsLayer`. Share turns that layer into the bitmap, so the picture is exactly the preview. PNG for flat
  backgrounds, JPEG 92 for photo backgrounds.
- **Backgrounds**: Paper, Sepia, Night, Dusk blue, the book's cover blurred (shrunk to 135 px wide, three box-blur
  passes each way on the CPU, stretched when drawn; a few milliseconds once per screen, same on every phone), or the
  listener's own picture from the photo picker (no storage permission, decoded at most 1920 px on its long side)
  under a dark scrim so white text stays readable, with "Use a picture you took or are allowed to share."
- **Type**: Tiro Devanagari Hindi and Tiro Bangla (Tiro Typeworks, OFL 1.1) for the quote, trimmed with fonttools to
  their script plus Latin and punctuation, OpenType layout kept, hinting dropped: 239 KB + 172 KB on disk, about
  **0.2 MB compressed** in the APK. English quotes use Tiro Devanagari Hindi's Latin. Credits and controls use the
  app's theme type.
- **Own activity** (`ShareCardActivity`, not exported) inside the same privacy gate (app lock, Hide app content), so
  any Share button opens it with `ShareEntry.quote(...)` or `ShareEntry.book(...)` and no route in the app's
  navigation. Files go to `cacheDir/shared-cards` (its own FileProvider, the 5 newest kept), then Android's share
  sheet. Nothing is uploaded by ListenLock.
- **Book card**: the library's book menu gets "Share this book" (cover, title, author, "Now listening").

## Alternatives

- **Canvas drawing by hand**: deterministic, but a second layout of the same design and its own text shaping for
  Hindi and Bengali; Compose gives one design for preview and picture.
- **Offscreen composition** (a `ComposeView` with no window): not reliable without a window; the card is on screen
  anyway, so recording what is drawn is simpler.
- **Noto from the phone** for quotes: 0 KB, but differs by phone maker and lacks the bookish look the design system
  chose.
- **Blur effect** (`RenderEffect`): GPU work and a hardware layer per frame, and a captured layer may not carry the
  effect into the shared picture. The CPU blur is the same picture on screen and in the file.
- **Two-scale colour wash** (shrink to 24 px, grow back): the first version; cheaper still, but blocky rather than a
  true blur. Replaced at the maintainer's request.

## Consequences

- APK about +0.2 MB (fonts) plus code. No new dependency, no permission, no network.
- Cold start unchanged: the fonts and the activity load only when a card screen opens. Memory while sharing: one
  1080 x 1920 bitmap (8 MB) for the moment it is written; the preview's layer lives only while the screen is open.
- The stats thread's share card and the video export reuse `ShareCardFrame` and `CardRecorder`.
