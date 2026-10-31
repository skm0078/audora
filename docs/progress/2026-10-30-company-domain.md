# Shares, the site and the privacy policy move to listenlock.telosmindlabs.com (2026-10-30, ADR 2026-10-30-company-domain)
- Every share's link is now `https://listenlock.telosmindlabs.com/get/`, and share pictures print
  `listenlock.telosmindlabs.com` under the ListenLock mark. Settings → Privacy policy opens
  `https://listenlock.telosmindlabs.com/privacy.html`. The address is kept in one place, `SiteConfig`.
- The site: `docs/CNAME` names the subdomain, Jekyll's `url`/`baseurl` move to it, the home and "get" pages carry the
  new canonical and preview links, and the site and privacy policy give `support@telosmindlabs.com` instead of a Gmail
  address. RELEASING.md says how the domain goes live (through prod, never the settings box first).
- "What's new" entry: none. `:pdf` not changed. No new dependency.
- **Could affect:** share cards (the line under the mark), share captions, Settings → Privacy policy, the site's
  links and previews.
- **Verified in CI:** `SiteConfigTest`, `ShareLinkConfigTest`, `CardMarkLinkTest` (the host on one line inside every
  card shape), release journey step 02 (extended: the launcher name and every link the app hands out).

**Real-device checks (the test phone):**
1. Share a quote card: under the ListenLock mark it reads `listenlock.telosmindlabs.com`, on one line, readable.
2. Share text: the last line is `https://listenlock.telosmindlabs.com/get/`.
3. Settings → Privacy policy opens the policy at `listenlock.telosmindlabs.com` (once this is on prod).

**After the release that brings this to prod:** https://github.com/skm0078/audora/settings/pages shows the custom
domain; tick Enforce HTTPS. Then https://skm0078.github.io/audora/ redirects to the new address.

**Play launch readiness (same PR, it shares RELEASING.md):**
- `docs/store/listing.md`: the Play listing text in English, Hindi and Bengali (Hindi and Bengali need a native read
  before upload), graphics with their licence status, category and contact.
- RELEASING.md: developer name TelosMind Labs with `support@`, the Data safety wording now names web pages and
  catalogues, and a new "Play Console app content" section with the answers for ads, app access, content rating,
  target audience and the four foreground-service declarations (`mediaPlayback`, `mediaProcessing`, `dataSync`,
  `microphone`) with what each video shows.
- ADR-061's network row lists the web-page and catalogue fetches that came after it; the answer is unchanged.
- GitHub releases are titled "ListenLock vX.Y.Z" (the APK file name stays, so Obtainium keeps updating).
