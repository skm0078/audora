# Share lines as a picture: quote cards with the book, author and page (2026-10-27, ADR 2026-10-27-share-cards)
- Sentence menu: one **Share…** row opens As audio, As a picture and As text for the same passage (a highlight's
  whole quote, else the sentence). As a picture opens the new card screen (`ui/share/`).
- Card screen: the picture as it will be shared, filling the space above the controls (side by side on a short wide
  window); sizes Square, Story 9:16, Post 4:5; backgrounds Paper, Sepia, Night, Dusk blue, the book's cover or your
  own picture; optional dedication; Share. At most 60 words, cut at a sentence end when close (`core/share/CardQuote`).
  Title, author, page and the ListenLock mark are always on.
- Library book menu: **Share this book** makes a card of the cover, title and author.
- Tiro Devanagari Hindi and Tiro Bangla for quotes, trimmed to about **0.2 MB** in the APK; credited on Voices &
  licences, OFL texts in `assets/licences/`.
- Cost: APK about +0.2 MB; cold start unchanged (loaded only on the card screen); one 8 MB bitmap while a story card is
  written; no new dependency, permission or network.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-27-share-cards.txt`. No `:pdf` change.
- **Could affect:** the sentence menu (Share rows moved into one), the library book menu (one more row).
- **Verified in CI:** `CardQuoteTest` (60-word cut, sentence ends in en/hi/bn, tidying, script), `BookActionTest`,
  `SentenceMenuTest` (Share choices, picture of a highlight and of a sentence), `ShareCardScreenTest` (story, square
  and post pictures decode at exactly 1080 x 1920 / 1080 x 1080 / 1080 x 1350; English 1x, Hindi and Bengali 2x;
  screenshots of the screen and the pictures), `ReleaseJourneyTest` step 19 (card made from the installed app, share
  sheet opens, Back returns).

**Real-device checks (the test phone):** long-press a sentence in an English, a Hindi and a Bengali book, Share… →
Share as a picture: the quote's letters join correctly (conjuncts, matras), the page reads in the book's language,
the mark sits in the corner. Try each size and background, pick your own photo, add a dedication, share to WhatsApp
Status: the picture is sharp and nothing is cut off. Library → a book's menu → Share this book.
