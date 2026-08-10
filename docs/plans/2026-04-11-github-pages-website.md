# CaseHub GitHub Pages Website — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a GitHub Pages site at `mdproctor.github.io/casehub` with a landing page, all four blog posts, and a docs placeholder, using full Jekyll in `docs/` following the Sparge pattern.

**Architecture:** Full Jekyll site in `docs/` directory. Single `default.html` layout wraps everything; specialised layouts (`landing.html`, `post.html`, `doc.html`) extend it. GitHub Actions builds and deploys on push to `main`. SC2 Muted Teal colour scheme via CSS custom properties in a single `main.css`.

**Tech Stack:** Jekyll 4.3, Ruby 3.3, GitHub Actions (actions/deploy-pages@v4), kramdown/GFM. Reference implementation: `/Users/mdproctor/claude/sparge/docs/`.

---

## File Map

| Action | Path |
|--------|------|
| Create | `docs/Gemfile` |
| Create | `docs/_config.yml` |
| Create | `docs/index.html` |
| Create | `docs/_layouts/default.html` |
| Create | `docs/_layouts/landing.html` |
| Create | `docs/_layouts/post.html` |
| Create | `docs/_layouts/doc.html` |
| Create | `docs/assets/css/main.css` |
| Create | `docs/blog/index.html` |
| Create | `docs/docs-site/index.md` |
| Move+edit | `docs/blog/2026-03-27-mdp01-*.md` → `docs/_posts/` |
| Move+edit | `docs/blog/2026-03-28-mdp02-*.md` → `docs/_posts/` |
| Move+edit | `docs/blog/2026-04-09-mdp03-*.md` → `docs/_posts/` |
| Move+edit | `docs/blog/2026-04-09-mdp04-*.md` → `docs/_posts/` |
| Create | `.github/workflows/pages.yml` |
| Modify | `CLAUDE.md` |

---

## Task 1: Jekyll Scaffold

**Files:**
- Create: `docs/Gemfile`
- Create: `docs/_config.yml`

- [ ] **Step 1: Create `docs/Gemfile`**

```ruby
source "https://rubygems.org"

gem "jekyll", "~> 4.3"
gem "csv"
gem "base64"
gem "bigdecimal"
gem "ostruct"

group :jekyll_plugins do
  gem "jekyll-feed", "~> 0.17"
end
```

- [ ] **Step 2: Create `docs/_config.yml`**

```yaml
title: CaseHub
description: "Collaborative AI problem-solving, built for Quarkus."
baseurl: "/casehub"
url: "https://mdproctor.github.io"
author: Mark Proctor

permalink: pretty

markdown: kramdown
kramdown:
  input: GFM

plugins:
  - jekyll-feed

exclude:
  - superpowers/
  - adr/
  - research/
  - summaries/
  - design-snapshots/
  - blog/mockups/
  - DESIGN.md
  - retro-issues.md
  - Gemfile
  - Gemfile.lock

defaults:
  - scope:
      path: ""
      type: "posts"
    values:
      layout: post
      permalink: /blog/:year/:month/:day/:title/
  - scope:
      path: "docs-site"
      type: "pages"
    values:
      layout: doc
```

- [ ] **Step 3: Create required directories**

```bash
mkdir -p docs/_layouts docs/_posts docs/assets/css docs/blog docs/docs-site
```

- [ ] **Step 4: Install gems**

```bash
cd docs && bundle install
```

Expected: gems install, `Gemfile.lock` created.

- [ ] **Step 5: Commit**

```bash
git add docs/Gemfile docs/_config.yml
git commit -m "feat(site): add Jekyll scaffold — Gemfile and _config.yml"
```

---

## Task 2: Default Layout

**Files:**
- Create: `docs/_layouts/default.html`
- Create: `docs/index.html` (temporary stub — replaced in Task 5)

The default layout is the single source of truth for nav and footer. All other layouts extend it.

- [ ] **Step 1: Create `docs/_layouts/default.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>{% if page.title and page.title != 'Home' %}{{ page.title }} — {% endif %}{{ site.title }}</title>
  <meta name="description" content="{{ page.excerpt | default: site.description | strip_html | normalize_whitespace | truncate: 160 }}">
  <link rel="stylesheet" href="{{ '/assets/css/main.css' | relative_url }}">
</head>
<body{% if page.layout == 'landing' %} class="has-hero"{% endif %}>
  <nav class="site-nav">
    <a class="nav-logo" href="{{ '/' | relative_url }}">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <rect x="2" y="2" width="9" height="9" rx="1.5" fill="#2aa8c4" opacity="0.9"/>
        <rect x="13" y="2" width="9" height="9" rx="1.5" fill="#2aa8c4" opacity="0.6"/>
        <rect x="2" y="13" width="9" height="9" rx="1.5" fill="#2aa8c4" opacity="0.6"/>
        <rect x="13" y="13" width="9" height="9" rx="1.5" fill="#2aa8c4" opacity="0.3"/>
      </svg>
      <span>CaseHub</span>
    </a>
    <div class="nav-links">
      <a href="{{ '/docs-site/' | relative_url }}" class="nav-link{% if page.layout == 'doc' %} active{% endif %}">Docs</a>
      <a href="{{ '/blog/' | relative_url }}" class="nav-link{% if page.layout == 'post' or page.url contains '/blog/' %} active{% endif %}">Blog</a>
      <a href="https://github.com/mdproctor/casehub" class="nav-link" target="_blank" rel="noopener">GitHub &#x2197;</a>
    </div>
  </nav>
  {{ content }}
  <footer class="site-footer">
    <div class="footer-inner">
      <span class="footer-logo">CaseHub</span>
      <div class="footer-links">
        <a href="{{ '/docs-site/' | relative_url }}">Docs</a>
        <a href="{{ '/blog/' | relative_url }}">Blog</a>
        <a href="https://github.com/mdproctor/casehub" target="_blank" rel="noopener">GitHub</a>
      </div>
      <span class="footer-tagline">Apache 2.0</span>
    </div>
  </footer>
</body>
</html>
```

- [ ] **Step 2: Create stub `docs/index.html` so Jekyll has something to build**

```html
---
layout: default
title: Home
---
<div style="padding:200px 48px;text-align:center;color:#2aa8c4;">CaseHub — coming soon</div>
```

- [ ] **Step 3: Verify Jekyll builds**

```bash
cd docs && bundle exec jekyll build 2>&1
```

Expected: `Build complete! → _site/` with no errors. If you see a gem error about `csv` or `base64`, those are already in the Gemfile — run `bundle install` again.

- [ ] **Step 4: Commit**

```bash
git add docs/_layouts/default.html docs/index.html
git commit -m "feat(site): add default layout with nav and footer"
```

---

## Task 3: CSS — SC2 Muted Teal Theme

**Files:**
- Create: `docs/assets/css/main.css`

Single stylesheet. All colour decisions live in `:root` variables. Touch only the variables section when tweaking the palette.

- [ ] **Step 1: Create `docs/assets/css/main.css`**

```css
/* ── Variables ─────────────────────────────────────── */
:root {
  --bg-deep:    #080d12;
  --bg-card:    #0e1820;
  --border:     #1a2e38;
  --accent:     #2aa8c4;
  --text:       #b8d8e0;
  --text-muted: #4a7a8a;
  --sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --mono: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
}

/* ── Reset ──────────────────────────────────────────── */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { scroll-behavior: smooth; }
body { font-family: var(--sans); line-height: 1.6; color: var(--text); background: var(--bg-deep); }
a { color: inherit; text-decoration: none; }
img { max-width: 100%; height: auto; display: block; border-radius: 6px; }

/* ── Site Nav ───────────────────────────────────────── */
.site-nav {
  position: absolute; top: 0; left: 0; right: 0; z-index: 100;
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 48px;
}
.nav-logo {
  display: flex; align-items: center; gap: 10px;
  color: var(--accent); font-size: 13px; font-weight: 700;
  letter-spacing: 3px; text-transform: uppercase;
}
.nav-logo svg { flex-shrink: 0; }
.nav-links { display: flex; gap: 28px; }
.nav-link { color: var(--text-muted); font-size: 13px; transition: color 0.2s; }
.nav-link:hover, .nav-link.active { color: var(--accent); }
body:not(.has-hero) .site-nav {
  position: static; background: var(--bg-card);
  border-bottom: 1px solid var(--border); padding: 14px 48px;
}

/* ── Hero ────────────────────────────────────────────── */
.hero {
  position: relative; min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  text-align: center; padding: 120px 24px 80px;
  overflow: hidden;
}
.hero-grid {
  position: absolute; inset: 0; z-index: 0;
  background-image:
    linear-gradient(rgba(42,168,196,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(42,168,196,0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  -webkit-mask-image: radial-gradient(ellipse 80% 60% at 50% 40%, black 30%, transparent 100%);
  mask-image: radial-gradient(ellipse 80% 60% at 50% 40%, black 30%, transparent 100%);
}
.hero::after {
  content: ''; position: absolute; inset: 0;
  background: radial-gradient(ellipse 60% 50% at 50% 50%, rgba(42,168,196,0.06) 0%, transparent 70%);
  z-index: 1;
}
.hero-content { position: relative; z-index: 2; max-width: 700px; }
.hero-eyebrow {
  color: var(--accent); font-size: 10px; font-weight: 700;
  letter-spacing: 4px; text-transform: uppercase; margin-bottom: 20px;
}
.hero-title {
  color: var(--text); font-size: clamp(30px, 5vw, 50px); font-weight: 700;
  line-height: 1.2; margin-bottom: 16px;
}
.hero-title span { color: var(--accent); }
.hero-sub {
  color: var(--text-muted); font-size: 15px; line-height: 1.75;
  margin-bottom: 36px; max-width: 560px; margin-left: auto; margin-right: auto;
}
.hero-btns { display: flex; gap: 14px; justify-content: center; flex-wrap: wrap; }
.btn-primary {
  background: var(--accent); color: var(--bg-deep);
  padding: 12px 28px; border-radius: 4px; font-weight: 700; font-size: 13px;
  letter-spacing: 0.5px; transition: opacity 0.2s;
}
.btn-primary:hover { opacity: 0.85; }
.btn-ghost {
  border: 1px solid var(--border); color: var(--text-muted);
  padding: 12px 28px; border-radius: 4px; font-size: 13px;
  transition: border-color 0.2s, color 0.2s;
}
.btn-ghost:hover { border-color: var(--accent); color: var(--accent); }

/* ── Stats Strip ──────────────────────────────────────── */
.stats-strip {
  background: var(--bg-card); border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  display: flex; justify-content: center; gap: 56px; padding: 22px; flex-wrap: wrap;
}
.stat { text-align: center; }
.stat-num { display: block; color: var(--accent); font-size: 18px; font-weight: 700; letter-spacing: 1px; }
.stat-label { display: block; color: var(--text-muted); font-size: 10px; text-transform: uppercase; letter-spacing: 1px; margin-top: 2px; }

/* ── Features ─────────────────────────────────────────── */
.features { padding: 80px 48px; max-width: 960px; margin: 0 auto; }
.features-header { text-align: center; margin-bottom: 56px; }
.features-header h2 { font-size: 26px; color: var(--text); margin-bottom: 8px; font-weight: 600; }
.features-header p { color: var(--text-muted); font-size: 15px; }
.feature-row { display: flex; align-items: flex-start; gap: 48px; margin-bottom: 56px; }
.feature-row.reverse { flex-direction: row-reverse; }
.feature-text { flex: 1; }
.feature-icon { color: var(--accent); font-size: 20px; margin-bottom: 12px; }
.feature-text h3 { font-size: 18px; font-weight: 600; color: var(--text); margin-bottom: 10px; }
.feature-text p { color: var(--text-muted); font-size: 14px; line-height: 1.8; }
.feature-box {
  flex: 1; background: var(--bg-card); border: 1px solid var(--border);
  border-radius: 6px; padding: 20px; font-size: 12px; font-family: var(--mono);
  color: var(--text-muted); min-height: 140px; line-height: 1.7;
}
.feature-box .kw { color: var(--accent); }
.feature-box .str { color: #7ecfdf; }
.feature-box .cm { color: #2d5060; }
@media (max-width: 700px) {
  .feature-row, .feature-row.reverse { flex-direction: column; }
}

/* ── Blog Preview ─────────────────────────────────────── */
.blog-preview {
  background: var(--bg-card); border-top: 1px solid var(--border);
  padding: 64px 48px;
}
.blog-preview h2 {
  text-align: center; font-size: 22px; font-weight: 600; color: var(--text);
  margin-bottom: 32px; letter-spacing: 0.5px;
}
.blog-cards { display: flex; gap: 20px; max-width: 960px; margin: 0 auto 32px; flex-wrap: wrap; }
.blog-card {
  flex: 1; min-width: 220px; background: var(--bg-deep);
  border: 1px solid var(--border); border-radius: 6px; padding: 20px;
  transition: border-color 0.2s; display: block;
}
.blog-card:hover { border-color: var(--accent); }
.card-date { font-size: 10px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
.blog-card h3 { font-size: 14px; font-weight: 600; color: var(--text); margin: 8px 0; line-height: 1.4; }
.blog-card p { font-size: 12px; color: var(--text-muted); line-height: 1.6; }
.card-tag {
  display: inline-block; margin-top: 12px; font-size: 10px; color: var(--accent);
  border: 1px solid var(--border); padding: 2px 8px; border-radius: 3px;
  text-transform: uppercase; letter-spacing: 0.5px;
}
.read-all { display: block; text-align: center; color: var(--accent); font-size: 13px; }
.read-all:hover { text-decoration: underline; }

/* ── Footer ───────────────────────────────────────────── */
.site-footer { background: var(--bg-deep); border-top: 1px solid var(--border); padding: 24px 48px; }
.footer-inner {
  max-width: 960px; margin: 0 auto;
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px;
}
.footer-logo { color: var(--accent); font-size: 12px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase; }
.footer-links { display: flex; gap: 20px; }
.footer-links a { color: var(--text-muted); font-size: 12px; }
.footer-links a:hover { color: var(--accent); }
.footer-tagline { color: var(--border); font-size: 11px; }

/* ── Blog Post ────────────────────────────────────────── */
.post-shell { display: flex; max-width: 1100px; margin: 0 auto; min-height: calc(100vh - 120px); }
.post-sidebar {
  width: 220px; flex-shrink: 0; padding: 80px 24px 40px 48px;
  border-right: 1px solid var(--border);
}
.sidebar-back { font-size: 12px; color: var(--text-muted); margin-bottom: 32px; display: block; }
.sidebar-back:hover { color: var(--accent); }
.sidebar-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 10px; }
.sidebar-posts { list-style: none; }
.sidebar-posts li { margin-bottom: 6px; }
.sidebar-posts a { font-size: 12px; color: var(--text-muted); line-height: 1.4; display: block; }
.sidebar-posts a:hover, .sidebar-posts a.active { color: var(--accent); }
.post-content { flex: 1; padding: 80px 48px 80px 40px; max-width: 720px; }
.post-header { margin-bottom: 40px; }
.post-date { font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; display: block; margin-bottom: 12px; }
.post-header h1 { font-size: clamp(24px, 3.5vw, 36px); font-weight: 700; color: var(--text); line-height: 1.25; margin-bottom: 16px; }
.post-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.post-tags .tag {
  font-size: 10px; color: var(--accent); border: 1px solid var(--border);
  padding: 2px 8px; border-radius: 3px; text-transform: uppercase; letter-spacing: 0.5px;
}
.post-content .post-body h2 { font-size: 20px; font-weight: 600; color: var(--text); margin: 36px 0 14px; }
.post-content .post-body h3 { font-size: 16px; font-weight: 600; color: var(--text); margin: 28px 0 10px; }
.post-content .post-body p { color: var(--text-muted); font-size: 15px; line-height: 1.8; margin-bottom: 18px; }
.post-content .post-body ul, .post-content .post-body ol { color: var(--text-muted); font-size: 15px; line-height: 1.8; padding-left: 20px; margin-bottom: 18px; }
.post-content .post-body li { margin-bottom: 6px; }
.post-content .post-body strong { color: var(--text); font-weight: 600; }
.post-content .post-body a { color: var(--accent); }
.post-content .post-body a:hover { text-decoration: underline; }
.post-content .post-body code { font-family: var(--mono); font-size: 13px; background: var(--bg-card); border: 1px solid var(--border); padding: 1px 5px; border-radius: 3px; color: var(--text); }
.post-content .post-body pre { background: var(--bg-card); border: 1px solid var(--border); border-radius: 6px; padding: 18px; overflow-x: auto; margin-bottom: 20px; }
.post-content .post-body pre code { background: none; border: none; padding: 0; font-size: 13px; }
.post-content .post-body blockquote { border-left: 3px solid var(--accent); padding-left: 16px; margin: 20px 0; }
.post-content .post-body blockquote p { color: var(--text-muted); font-style: italic; }
.post-content .post-body img { border: 1px solid var(--border); border-radius: 6px; margin: 20px 0; }
.post-content .post-body table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px; }
.post-content .post-body th { background: var(--bg-card); color: var(--text); font-weight: 600; padding: 10px 14px; border: 1px solid var(--border); text-align: left; }
.post-content .post-body td { color: var(--text-muted); padding: 8px 14px; border: 1px solid var(--border); }
@media (max-width: 768px) {
  .post-shell { flex-direction: column; }
  .post-sidebar { width: 100%; padding: 80px 24px 20px; border-right: none; border-bottom: 1px solid var(--border); }
  .post-content { padding: 24px; }
}

/* ── Blog Index ───────────────────────────────────────── */
.blog-index { max-width: 760px; margin: 0 auto; padding: 100px 48px 80px; }
.blog-index h1 { font-size: 28px; font-weight: 700; color: var(--text); margin-bottom: 8px; }
.blog-index .subtitle { color: var(--text-muted); font-size: 14px; margin-bottom: 48px; }
.post-list { list-style: none; }
.post-list-item { border-bottom: 1px solid var(--border); padding: 24px 0; }
.post-list-item:last-child { border-bottom: none; }
.post-list-item .meta { font-size: 10px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 8px; }
.post-list-item a.title { font-size: 18px; font-weight: 600; color: var(--text); display: block; margin-bottom: 8px; }
.post-list-item a.title:hover { color: var(--accent); }
.post-list-item .excerpt { font-size: 14px; color: var(--text-muted); line-height: 1.7; }
.post-list-item .tags { margin-top: 10px; display: flex; gap: 8px; }
.post-list-item .tags .tag { font-size: 10px; color: var(--accent); border: 1px solid var(--border); padding: 2px 8px; border-radius: 3px; text-transform: uppercase; letter-spacing: 0.5px; }

/* ── Doc Placeholder ──────────────────────────────────── */
.doc-shell { max-width: 760px; margin: 0 auto; padding: 100px 48px 80px; }
.doc-shell h1 { font-size: 28px; font-weight: 700; color: var(--text); margin-bottom: 16px; }
.doc-shell p { color: var(--text-muted); font-size: 15px; line-height: 1.8; }
.doc-shell a { color: var(--accent); }
.doc-shell a:hover { text-decoration: underline; }
```

- [ ] **Step 2: Rebuild and verify CSS loads**

```bash
cd docs && bundle exec jekyll build 2>&1
```

Open `docs/_site/index.html` in a browser or run `bundle exec jekyll serve` and visit `http://localhost:4000/casehub/`. The page should be dark (`#080d12` background) with the teal nav logo.

- [ ] **Step 3: Commit**

```bash
git add docs/assets/css/main.css
git commit -m "feat(site): add SC2 Muted Teal stylesheet"
```

---

## Task 4: Landing Layout

**Files:**
- Create: `docs/_layouts/landing.html`
- Modify: `docs/index.html` (switch from stub to full landing)

- [ ] **Step 1: Create `docs/_layouts/landing.html`**

```html
---
layout: default
---
<section class="hero">
  <div class="hero-grid"></div>
  <div class="hero-content">
    <p class="hero-eyebrow">Open Source Framework</p>
    <h1 class="hero-title">Collaborative AI problem&#8209;solving,<br>built for <span>Quarkus</span></h1>
    <p class="hero-sub">Blending Blackboard Architecture with CMMN, where orchestration meets choreography for Agentic AI.</p>
    <div class="hero-btns">
      <a href="https://github.com/mdproctor/casehub" class="btn-primary" target="_blank" rel="noopener">View on GitHub &#x2197;</a>
      <a href="{{ '/blog/' | relative_url }}" class="btn-ghost">Read the blog &rarr;</a>
    </div>
  </div>
</section>

<div class="stats-strip">
  <div class="stat"><span class="stat-num">Java 21</span><span class="stat-label">Runtime</span></div>
  <div class="stat"><span class="stat-num">Quarkus 3.17</span><span class="stat-label">Platform</span></div>
  <div class="stat"><span class="stat-num">5</span><span class="stat-label">Modules</span></div>
  <div class="stat"><span class="stat-num">CMMN</span><span class="stat-label">Semantics</span></div>
</div>

<section class="features">
  <div class="features-header">
    <h2>A framework that reasons, plans, and executes.</h2>
    <p>Two execution models. One shared workspace. Infinite agents.</p>
  </div>

  <div class="feature-row">
    <div class="feature-text">
      <div class="feature-icon">⬡</div>
      <h3>CaseFile — the shared workspace.</h3>
      <p>A structured key-value blackboard where agents declare what they need and what they produce. The CaseEngine runs the control loop — evaluating, planning, executing — until the case reaches quiescence or completion.</p>
    </div>
    <div class="feature-box">
      <span class="cm">// TaskDefinition declares its contract</span><br>
      <span class="kw">entry</span>: [<span class="str">"document.text"</span>, <span class="str">"doc.language"</span>]<br>
      <span class="kw">produces</span>: [<span class="str">"summary"</span>, <span class="str">"sentiment"</span>]<br><br>
      <span class="cm">// CaseEngine resolves the graph</span><br>
      engine.<span class="kw">createAndSolve</span>(caseType, init)<br>
      &nbsp;&nbsp;&rarr; evaluate &rarr; plan &rarr; execute &rarr; repeat
    </div>
  </div>

  <div class="feature-row reverse">
    <div class="feature-text">
      <div class="feature-icon">⇄</div>
      <h3>Dual execution model.</h3>
      <p>Run collaborative cases through the CaseFile model, or dispatch targeted requests through the TaskBroker. Autonomous workers monitor external systems and self-initiate — all tracked with full propagation lineage.</p>
    </div>
    <div class="feature-box">
      <span class="cm">// Orchestrated — CaseEngine drives</span><br>
      CaseEngine.<span class="kw">createAndSolve</span>(...)<br><br>
      <span class="cm">// Choreographed — Workers self-initiate</span><br>
      registry.<span class="kw">notifyAutonomousWork</span>(<br>
      &nbsp;&nbsp;TaskOrigin.<span class="kw">AUTONOMOUS</span>, payload<br>
      )
    </div>
  </div>

  <div class="feature-row">
    <div class="feature-text">
      <div class="feature-icon">⟳</div>
      <h3>Resilience built in.</h3>
      <p>RetryPolicy, TimeoutEnforcer, PoisonPillDetector, DeadLetterQueue, and IdempotencyService ship with the core module. Your agents fail gracefully without writing a single line of retry logic.</p>
    </div>
    <div class="feature-box">
      <span class="kw">casehub</span>.retry.max-attempts=3<br>
      <span class="kw">casehub</span>.timeout.check-interval=5s<br>
      <span class="kw">casehub</span>.dlq.enabled=true<br><br>
      <span class="cm">// Idempotency built into execution</span><br>
      IdempotencyService.<span class="kw">guard</span>(taskId, fn)
    </div>
  </div>
</section>

<section class="blog-preview">
  <h2>Development diary.</h2>
  <div class="blog-cards">
    {% for post in site.posts limit: 3 %}
    <a href="{{ post.url | relative_url }}" class="blog-card">
      <span class="card-date">{{ post.date | date: "%-d %b %Y" }}</span>
      <h3>{{ post.title }}</h3>
      <p>{{ post.excerpt | strip_html | truncate: 110 }}</p>
      {% if post.tags.first %}<span class="card-tag">{{ post.tags.first }}</span>{% endif %}
    </a>
    {% endfor %}
  </div>
  <a href="{{ '/blog/' | relative_url }}" class="read-all">Read all posts &rarr;</a>
</section>
```

- [ ] **Step 2: Update `docs/index.html` to use the landing layout**

Replace the entire contents of `docs/index.html` with:

```html
---
layout: landing
title: Home
---
```

- [ ] **Step 3: Verify build**

```bash
cd docs && bundle exec jekyll build 2>&1
```

Expected: builds cleanly. The blog preview section will be empty until posts are added in Task 6 — that is fine.

- [ ] **Step 4: Commit**

```bash
git add docs/_layouts/landing.html docs/index.html
git commit -m "feat(site): add landing layout — hero, stats, features, blog preview"
```

---

## Task 5: Post Layout

**Files:**
- Create: `docs/_layouts/post.html`

- [ ] **Step 1: Create `docs/_layouts/post.html`**

```html
---
layout: default
---
<div class="post-shell">
  <aside class="post-sidebar">
    <a href="{{ '/' | relative_url }}" class="sidebar-back">&larr; Home</a>
    <div class="sidebar-label">Recent posts</div>
    <ul class="sidebar-posts">
      {% for p in site.posts limit: 8 %}
      <li>
        <a href="{{ p.url | relative_url }}"
           class="{% if p.url == page.url %}active{% endif %}">
          {{ p.title }}
        </a>
      </li>
      {% endfor %}
    </ul>
  </aside>
  <article class="post-content">
    <header class="post-header">
      <time class="post-date" datetime="{{ page.date | date_to_xmlschema }}">
        {{ page.date | date: "%-d %B %Y" }}
      </time>
      <h1>{{ page.title }}</h1>
      {% if page.tags %}
      <div class="post-tags">
        {% for tag in page.tags %}<span class="tag">{{ tag }}</span>{% endfor %}
      </div>
      {% endif %}
    </header>
    <div class="post-body">
      {{ content }}
    </div>
  </article>
</div>
```

- [ ] **Step 2: Verify build**

```bash
cd docs && bundle exec jekyll build 2>&1
```

Expected: clean build. No posts exist yet so no post pages are generated — that is fine.

- [ ] **Step 3: Commit**

```bash
git add docs/_layouts/post.html
git commit -m "feat(site): add post layout with sidebar"
```

---

## Task 6: Doc Layout and Docs Placeholder

**Files:**
- Create: `docs/_layouts/doc.html`
- Create: `docs/docs-site/index.md`

- [ ] **Step 1: Create `docs/_layouts/doc.html`**

```html
---
layout: default
---
<div class="doc-shell">
  {{ content }}
</div>
```

- [ ] **Step 2: Create `docs/docs-site/index.md`**

```markdown
---
layout: doc
title: Documentation
---

# Documentation

Full documentation is coming soon.

In the meantime, the [DESIGN.md on GitHub](https://github.com/mdproctor/casehub/blob/main/docs/DESIGN.md) contains the complete architecture specification — covering the CaseFile model, CaseEngine control loop, dual execution model, resilience layer, and module structure.
```

- [ ] **Step 3: Verify build**

```bash
cd docs && bundle exec jekyll build 2>&1
```

Confirm `docs/_site/docs-site/index.html` exists.

- [ ] **Step 4: Commit**

```bash
git add docs/_layouts/doc.html docs/docs-site/index.md
git commit -m "feat(site): add doc layout and docs placeholder"
```

---

## Task 7: Migrate Blog Posts to `_posts/`

**Files:**
- Move + edit: all four files in `docs/blog/` → `docs/_posts/`

Jekyll requires posts in `_posts/` with filename format `YYYY-MM-DD-slug.md` and frontmatter containing at minimum `layout`, `title`, and `date`. The existing files have neither frontmatter nor the right location. This task moves them and adds frontmatter. The body content is **not changed**.

- [ ] **Step 1: Move post 1 and add frontmatter**

```bash
cp docs/blog/2026-03-27-mdp01-wanted-sketch-got-framework.md \
   docs/_posts/2026-03-27-mdp01-wanted-sketch-got-framework.md
```

Open `docs/_posts/2026-03-27-mdp01-wanted-sketch-got-framework.md` and **prepend** the following frontmatter block before the existing `# Wanted a Sketch, Got a Framework` heading:

```yaml
---
layout: post
title: "Wanted a Sketch, Got a Framework"
date: 2026-03-27
tags: [day-zero, architecture]
excerpt: "One session, 73 files, 14,003 lines of code — what started as a request for a sketch became a working framework."
---
```

Then delete the original from `docs/blog/`:

```bash
rm docs/blog/2026-03-27-mdp01-wanted-sketch-got-framework.md
```

- [ ] **Step 2: Move post 2 and add frontmatter**

```bash
cp docs/blog/2026-03-28-mdp02-architecture-behind-casehub.md \
   docs/_posts/2026-03-28-mdp02-architecture-behind-casehub.md
```

Prepend to `docs/_posts/2026-03-28-mdp02-architecture-behind-casehub.md`:

```yaml
---
layout: post
title: "The Architecture Behind CaseHub: Blackboard Meets CMMN"
date: 2026-03-28
tags: [architecture, cmmn]
excerpt: "Two patterns from very different traditions — Blackboard Architecture and CMMN — and why they belong together for agentic AI."
---
```

```bash
rm docs/blog/2026-03-28-mdp02-architecture-behind-casehub.md
```

- [ ] **Step 3: Move post 3 and add frontmatter**

```bash
cp docs/blog/2026-04-09-mdp03-pojo-graph-and-goals.md \
   docs/_posts/2026-04-09-mdp03-pojo-graph-and-goals.md
```

Prepend to `docs/_posts/2026-04-09-mdp03-pojo-graph-and-goals.md`:

```yaml
---
layout: post
title: "Session 3: Getting the Architecture Right"
date: 2026-04-09
tags: [architecture, design]
excerpt: "Collapsing the lineage graph, redesigning the persistence layer, and researching goal models across BDI, GOAP, CMMN, and HTN."
---
```

```bash
rm docs/blog/2026-04-09-mdp03-pojo-graph-and-goals.md
```

- [ ] **Step 4: Move post 4 and add frontmatter**

```bash
cp docs/blog/2026-04-09-mdp04-two-casehubs-one-design.md \
   docs/_posts/2026-04-09-mdp04-two-casehubs-one-design.md
```

Prepend to `docs/_posts/2026-04-09-mdp04-two-casehubs-one-design.md`:

```yaml
---
layout: post
title: "Two CaseHubs, One Design"
date: 2026-04-09
tags: [architecture, merge]
excerpt: "Discovering a parallel casehub-engine implementation and charting a 9-phase plan to unify both systems into one coherent design."
---
```

```bash
rm docs/blog/2026-04-09-mdp04-two-casehubs-one-design.md
```

- [ ] **Step 5: Verify all four posts build**

```bash
cd docs && bundle exec jekyll build 2>&1 | grep -E "(Post|post|error|Error|warning)"
```

Expected: four post files generated under `_site/blog/2026/`. No errors. Run `bundle exec jekyll serve` and navigate to `http://localhost:4000/casehub/blog/` to verify posts appear (the blog index will be built in Task 8).

- [ ] **Step 6: Commit**

```bash
git add docs/_posts/ docs/blog/
git commit -m "feat(site): migrate blog posts to _posts/ with Jekyll frontmatter"
```

---

## Task 8: Blog Index Page

**Files:**
- Create: `docs/blog/index.html`

- [ ] **Step 1: Create `docs/blog/index.html`**

```html
---
layout: default
title: Blog
---
<div class="blog-index">
  <h1>Development diary.</h1>
  <p class="subtitle">Building CaseHub — session by session.</p>
  <ul class="post-list">
    {% for post in site.posts %}
    <li class="post-list-item">
      <div class="meta">
        <time datetime="{{ post.date | date_to_xmlschema }}">{{ post.date | date: "%-d %B %Y" }}</time>
        {% if post.tags %} &middot; {% for tag in post.tags %}<span class="tag">{{ tag }}</span>{% endfor %}{% endif %}
      </div>
      <a href="{{ post.url | relative_url }}" class="title">{{ post.title }}</a>
      <p class="excerpt">{{ post.excerpt | strip_html | truncate: 160 }}</p>
    </li>
    {% endfor %}
  </ul>
</div>
```

- [ ] **Step 2: Verify blog index builds and links work**

```bash
cd docs && bundle exec jekyll build 2>&1
```

Confirm `docs/_site/blog/index.html` exists and contains all four post titles. Run `bundle exec jekyll serve` and navigate to `http://localhost:4000/casehub/blog/` to verify the listing renders correctly with all four posts.

- [ ] **Step 3: Commit**

```bash
git add docs/blog/index.html
git commit -m "feat(site): add blog index listing all posts"
```

---

## Task 9: GitHub Actions Workflow

**Files:**
- Create: `.github/workflows/pages.yml`

- [ ] **Step 1: Create `.github/workflows/pages.yml`**

```yaml
name: Deploy site to GitHub Pages

on:
  push:
    branches: ["main"]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: "pages"
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Ruby
        uses: ruby/setup-ruby@v1
        with:
          ruby-version: '3.3'
          bundler-cache: true
          working-directory: docs

      - name: Setup Pages
        id: pages
        uses: actions/configure-pages@v5

      - name: Build with Jekyll
        run: bundle exec jekyll build --baseurl "${{ steps.pages.outputs.base_path }}"
        working-directory: docs
        env:
          JEKYLL_ENV: production

      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: docs/_site

  deploy:
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    runs-on: ubuntu-latest
    needs: build
    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/pages.yml
git commit -m "feat(site): add GitHub Actions workflow for Pages deployment"
```

---

## Task 10: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add website section to CLAUDE.md**

Find the `## Project Structure` section in `CLAUDE.md` and add after the module tree:

```markdown
## Website

The project site is published at `https://mdproctor.github.io/casehub` via GitHub Pages.

Jekyll source lives in `docs/`. Build: `cd docs && bundle exec jekyll serve`.

**Blog posts are authored in `docs/_posts/`** (not `docs/blog/`). Filename format: `YYYY-MM-DD-slug.md`. Every post needs frontmatter:

```yaml
---
layout: post
title: "Post Title"
date: YYYY-MM-DD
tags: [tag1, tag2]
excerpt: "One sentence summary shown in listings and cards."
---
```

Do not add files to `docs/` without also adding them to the `exclude:` list in `docs/_config.yml` if they should not be published.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md — blog posts now authored in docs/_posts/"
```

---

## Task 11: Enable GitHub Pages (Manual Step)

This step is performed by the human in the GitHub web interface — it cannot be scripted.

- [ ] **Step 1: Push all commits to `main`**

```bash
git push origin main
```

- [ ] **Step 2: Enable GitHub Pages in repo settings**

1. Open `https://github.com/mdproctor/casehub/settings/pages`
2. Under **Source**, select **GitHub Actions**
3. Save

- [ ] **Step 3: Verify the Actions workflow runs**

```bash
gh run list --workflow=pages.yml --limit 5
```

Wait for the run to complete (`gh run watch`), then open `https://mdproctor.github.io/casehub` to confirm the site is live.

- [ ] **Step 4: Smoke-check all pages**

Visit each URL and confirm it renders correctly:

| URL | Expected |
|-----|----------|
| `https://mdproctor.github.io/casehub/` | Landing page — hero, stats, features, blog preview |
| `https://mdproctor.github.io/casehub/blog/` | Blog index — all 4 posts listed |
| `https://mdproctor.github.io/casehub/blog/2026/03/27/mdp01-wanted-sketch-got-framework/` | Post 1 full text |
| `https://mdproctor.github.io/casehub/blog/2026/03/28/mdp02-architecture-behind-casehub/` | Post 2 full text |
| `https://mdproctor.github.io/casehub/blog/2026/04/09/mdp03-pojo-graph-and-goals/` | Post 3 full text |
| `https://mdproctor.github.io/casehub/blog/2026/04/09/mdp04-two-casehubs-one-design/` | Post 4 full text |
| `https://mdproctor.github.io/casehub/docs-site/` | Docs placeholder |

---

## Self-Review

**Spec coverage:**
- ✅ Landing page with hero, stats, features, blog preview
- ✅ SC2 Muted Teal palette (`--accent: #2aa8c4`)
- ✅ All 4 blog posts migrated with frontmatter
- ✅ Blog index page
- ✅ Docs placeholder linking to DESIGN.md on GitHub
- ✅ Full Jekyll in `docs/` (no hybrid approach)
- ✅ Single `default.html` for nav/footer
- ✅ GitHub Actions workflow (Sparge pattern)
- ✅ `baseurl: /casehub`
- ✅ `docs/blog/mockups/` excluded from build
- ✅ CLAUDE.md updated

**Placeholder scan:** No TBDs or incomplete steps.

**Type consistency:** CSS class names used in layouts (`post-shell`, `post-sidebar`, `post-content`, `post-body`, `blog-index`, `doc-shell`, etc.) all defined in `main.css` Task 3. Jekyll variable names (`site.posts`, `page.url`, `page.tags`, `page.date`) are standard Jekyll — no custom naming inconsistencies.
