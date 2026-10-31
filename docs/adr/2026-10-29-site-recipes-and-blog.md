# Recipes and a blog on the site, built by GitHub Pages' own Jekyll (2026-10-29)

**Status:** accepted

## Context

The site (`prod` `/docs`) was hand-written HTML with `docs/.nojekyll`. The maintainer wants recipes (real situations
solved by combining features), guides and a blog they write themselves. Hand-writing HTML for every post would make
writing slow, and the site must keep its rules: served from `prod` `/docs`, no external requests, no analytics, no
cost.

## Decision

- Remove `docs/.nojekyll` and let GitHub Pages build `/docs` with its built-in Jekyll. Recipes are a collection in
  `docs/_recipes/`, posts are in `docs/_posts/`, both Markdown with front matter (docs/CONTENT.md).
- `index.html` and `privacy.html` have no front matter, so Jekyll copies them unchanged. Developer docs (ADRs,
  progress, specs) are excluded in `docs/_config.yml`; they stay readable on GitHub.
- Plugins only from Pages' allowed list, all build-time: `jekyll-feed` (RSS at `/blog/feed.xml`), `jekyll-sitemap`,
  `jekyll-seo-tag` (titles, descriptions, canonical links, Open Graph). The layouts keep the home page's
  Content-Security-Policy, so readers' browsers still fetch nothing from other sites.
- Content goes into `dev` by PR like code and reaches the site with each dev → prod promotion, so recipes never
  describe a feature people cannot install yet.

## Alternatives

- **Keep hand-written HTML:** no build, but every post is an HTML file with a copied header; too slow to write.
- **A static site generator in a GitHub Actions workflow (Hugo, Astro):** more control, but Pages would have to
  deploy from Actions instead of `prod` `/docs`, and it adds a workflow to maintain.
- **A hosted blog (Medium, Substack, Hashnode):** trackers and someone else's domain, against the site's privacy
  promise.

## Consequences

- Markdown files in `/docs` that are not excluded and have front matter become pages; new developer docs must be
  added to `exclude` in `docs/_config.yml`.
- A post waits for the next promotion. If promotions become rare, content-only PRs into `prod` can be reconsidered.
- No effect on the app or its size.
