# Writing recipes and blog posts

The public site is built by GitHub Pages from `prod` `/docs` with its built-in Jekyll
([ADR 2026-10-29-site-recipes-and-blog](adr/2026-10-29-site-recipes-and-blog.md)). You write Markdown; Pages turns
it into pages. The home page and privacy policy are hand-written HTML and are copied as they are.

| What | Where | Address on the site |
| --- | --- | --- |
| Recipe | `docs/_recipes/<slug>.md` | `/recipes/<slug>/` |
| Blog post | `docs/_posts/<yyyy-mm-dd>-<slug>.md` | `/blog/<yyyy>/<slug>/` |
| Draft post (not published) | `docs/_drafts/<slug>.md` | none |

Write in GitHub's web editor (**Add file → Create new file** on a branch) and open a PR into `dev`, like any other
change. Pages publishes from `prod`, so a recipe or post goes live with the next dev → prod promotion.

## A recipe

A recipe is one real situation solved by combining features, in exact steps. Title it by the situation
(“Fall asleep to a book”), never by the feature (“Using the sleep timer”).

```markdown
---
title: Fall asleep to a book without losing your place     # under 60 characters
description: One sentence, under 155 characters, shown in search results and share previews.
category: Everyday listening                                # Studying, Making, Work and documents, Accessibility…
order: 3                                                     # position on /recipes/
tags: [English, हिन्दी, বাংলা, Works offline, Screen off]
needs: Downloads with their real sizes, or "Nothing extra"
setup: 2 minutes, once
features: [Sleep timer, Sleep options, Focus sound]
cost: The battery or storage cost, in plain words
checked_with: "0.1.0"                                        # the version whose screens the steps were checked on
---

1. **Short action in bold.** The rest of the step. Use the exact on-screen names in **bold**.
2. …three to seven steps, one action each.

## Why it works

One or two sentences on why these features work well together.

## If it goes wrong

The most likely problem and how to fix it.
```

## A blog post

Copy `docs/_drafts/post-template.md` to `docs/_posts/<yyyy-mm-dd>-<slug>.md` and fill it in. Posts appear on
`/blog/` and in the RSS feed (`/blog/feed.xml`), newest first.

## Rules before publishing

- **Only what is on `prod`.** The site serves from `prod`, so a recipe must not promise a feature that is still only
  on `dev`.
- **Checked by ear.** Do not claim a voice or reading quality until it has been listened to on the test phone.
- **No personal details.** No real names, email addresses or phone models in the text, screenshots or file names.
  Posts are signed “TelosMind Labs” unless a pen name is chosen. Screenshots: clean status bar, no notifications.
- **Real numbers.** Download sizes and battery notes come from the app and [PERFORMANCE.md](PERFORMANCE.md).
- **No outside requests.** No embedded videos, fonts, scripts or images from other sites: put images and short sound
  clips (under 300 KB) in `docs/assets/`, or link out.
- **Search basics.** Title under 60 characters, description under 155, and the words people really search for
  (“Bengali PDF read aloud”) in the title and first sentence.

## Hindi and Bengali versions

Add the translation as its own file with its own address, so search engines can find it, for example
`docs/_recipes/hi/<slug>.md` with `lang: hi` and `permalink: /hi/recipes/<slug>/` in its front matter. Use the app's
own Hindi and Bengali on-screen words.

## Preview on a computer (optional)

```
cd docs
printf 'source "https://rubygems.org"\ngem "github-pages", group: :jekyll_plugins\ngem "webrick"\n' > Gemfile
bundle install && bundle exec jekyll serve --baseurl /audora
```

Then open http://localhost:4000/audora/. The Gemfile is only for previewing and is not committed.
