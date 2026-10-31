# ADR 2026-10-30-company-domain: ListenLock lives at listenlock.telosmindlabs.com

**Status:** Accepted (2026-10-30). The maintainer's decision: one company domain, every app on its own subdomain.

## Context
The site, the share link and the privacy policy lived at `skm0078.github.io/audora`, a personal address that would
break if the repository moved to a company account, and was too long to print on a share card. The site and privacy
page also showed a personal Gmail address. The company, TelosMind Labs, bought `telosmindlabs.com` on 2026-10-30.

## Decision
1. **One domain for the company, a subdomain per app.** `telosmindlabs.com` is the company's own site (its own
   repository). ListenLock is `listenlock.telosmindlabs.com`; later apps and services get their own (for example
   `pro.`, `api.`). No separate app domains.
2. **The address lives in one place, `SiteConfig` (`:core`).** `ShareLinkConfig.appLink` is `SiteConfig.GET_PAGE`;
   Settings → Privacy policy opens `SiteConfig.PRIVACY_PAGE` (it used to keep its own copy of the URL).
3. **Cards print the bare host** under the ListenLock mark (`ShareLinkConfig.cardLink`). It names both the app and
   the company, which is the quiet credit the share rule asks for. The card-link limit goes from 24 to 28 characters
   for it; a card is 360 card-dp wide and the line needs about 140, and `CardMarkLinkTest` checks it stays on one line
   inside every card shape.
4. **The custom domain reaches GitHub Pages only through a release.** `docs/CNAME` is added on `dev` and goes live
   when dev is merged into prod. Nobody types the domain into the repository's Pages settings first, because that
   box commits straight to `prod`. Jekyll's `url` and `baseurl` move to the new root.
5. **Public email is on the company domain:** `support@telosmindlabs.com` replaces the Gmail address on the site and
   in the privacy policy (the maintainer's rule: never a Gmail address in public).

## Consequences
- Old `skm0078.github.io/audora/...` links keep working: GitHub Pages redirects them to the custom domain.
- Moving the repository to a company GitHub account later no longer breaks the Play privacy URL.
- The domain must be renewed every year (auto-renew is on); a lapse takes the site, the share links, email and the
  Play privacy URL down together.
- `support@telosmindlabs.com` must receive mail before this change reaches `prod`.
