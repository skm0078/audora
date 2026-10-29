# Recipes and a blog on the site (2026-10-29, ADR 2026-10-29-site-recipes-and-blog)
- The site now has **Recipes** (`/recipes/`): six real situations solved by combining features, in exact on-screen
  steps (study a scanned textbook on a commute, revise before an exam, fall asleep to a book, turn photos of notes
  into an audiobook, read old Hindi and Bengali government PDFs, read with TalkBack or low vision). A **Blog**
  (`/blog/`, RSS at `/blog/feed.xml`) is ready for the first post.
- GitHub Pages builds `/docs` with its own Jekyll (`docs/.nojekyll` removed, `docs/_config.yml` added). The home page
  and privacy policy are copied unchanged; their footers link to Recipes and Blog. Developer docs are left out of
  the site. How to write content: `docs/CONTENT.md`. RELEASING.md now says Pages serves from `prod`.
- "What's new" entry: none (site only). `:pdf` not changed. No new app dependency; the three Jekyll plugins run on
  GitHub's side.
- **Could affect:** the site's home page and privacy page (should look exactly as before), `sitemap.xml`.
- **Verified:** built locally with the `github-pages` gem (the versions Pages runs): every page builds, the home page
  and privacy policy come out byte-for-byte the same as their sources, and no built page refers to another site except
  through ordinary links.

**Checks after the next promotion to prod (any browser):**
1. https://skm0078.github.io/audora/ and `/privacy.html` look as before; the footer has Recipes and Blog.
2. `/recipes/` lists six recipes; each opens with its facts box and steps, in light and dark mode, and on a phone.
3. `/blog/` shows the "first post arrives" note; `/blog/feed.xml` and `/sitemap.xml` open.
4. Paste a recipe link into a chat app: the preview shows its title, description and the feature graphic.
