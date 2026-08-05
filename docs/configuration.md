# Configuration

The pipeline reads Java properties from `wikihelp.properties`.

## Common properties

```properties
wikihelp.title=Product documentation
wikihelp.mode=offline
wikihelp.sources=public-wiki,internal-guides
```

Each source uses `wikihelp.source.<id>.*` properties. All adapters support these selectors:

```properties
wikihelp.source.public-wiki.include.tags=eclipse-help,public
wikihelp.source.public-wiki.tags.mode=any
wikihelp.source.public-wiki.exclude.tags=draft,obsolete
wikihelp.source.public-wiki.include.pages=Installation,Migration Guide
wikihelp.source.public-wiki.include.titleGlobs=*Guide,Getting Started*
wikihelp.source.public-wiki.exclude.titleGlobs=*Internal*
wikihelp.source.public-wiki.include.pathGlobs=docs/**
wikihelp.source.public-wiki.exclude.pathGlobs=docs/archive/**
```

`tags.mode=all` requires every included tag. Include selector dimensions are combined with AND.

## MediaWiki

Categories are exposed as tags. `contentMode=source` keeps MediaWiki markup; `rendered` requests server-rendered HTML.

```properties
wikihelp.source.media.type=mediawiki
wikihelp.source.media.apiUrl=https://www.example.org/w/api.php
wikihelp.source.media.pageBaseUrl=https://www.example.org/wiki/
wikihelp.source.media.language=en
wikihelp.source.media.contentMode=source
wikihelp.source.media.include.tags=Eclipse Help,Published
wikihelp.source.media.tags.mode=all
wikihelp.source.media.exclude.tags=Draft
wikihelp.source.media.maxPages=500
wikihelp.source.media.tokenEnvironment=MEDIAWIKI_TOKEN
```

With no categories or explicit pages, the adapter can enumerate a namespace using `namespace=0`.

## GitLab Wiki

GitLab Wiki has no universal page-label field. WikiHelp therefore reads tags from front matter or a `wikihelp-tags:` line in the source page before optionally requesting rendered HTML.

```markdown
---
tags: [eclipse-help, public]
---
# Installation
```

```properties
wikihelp.source.gitlab.type=gitlab
wikihelp.source.gitlab.baseUrl=https://gitlab.example.org
wikihelp.source.gitlab.apiBaseUrl=https://gitlab.example.org/api/v4
wikihelp.source.gitlab.project=group/project
wikihelp.source.gitlab.contentMode=rendered
wikihelp.source.gitlab.include.tags=eclipse-help
wikihelp.source.gitlab.include.pathGlobs=documentation/**
wikihelp.source.gitlab.tokenEnvironment=GITLAB_TOKEN
```

## Confluence

Confluence labels are exposed as tags and are pushed into the CQL discovery query. Cloud commonly uses `/wiki/rest/api`; Data Center commonly uses `/rest/api`.

```properties
wikihelp.source.confluence.type=confluence
wikihelp.source.confluence.baseUrl=https://example.atlassian.net
wikihelp.source.confluence.apiPath=/wiki/rest/api
wikihelp.source.confluence.space=DOC
wikihelp.source.confluence.include.tags=eclipse-help,published
wikihelp.source.confluence.tags.mode=all
wikihelp.source.confluence.exclude.tags=draft
wikihelp.source.confluence.userEnvironment=CONFLUENCE_USER
wikihelp.source.confluence.tokenEnvironment=CONFLUENCE_TOKEN
```

The adapter requests `body.export_view`, which preserves Confluence's own macro rendering better than attempting to interpret storage XML locally.

## Eclipsepedia archive

Eclipsepedia no longer exposes the old MediaWiki API. Its adapter discovers links from static category pages and converts archived HTML.

```properties
wikihelp.source.archive.type=eclipsepedia
wikihelp.source.archive.baseUrl=https://wiki.eclipse.org/
wikihelp.source.archive.include.tags=GEF
wikihelp.source.archive.exclude.titleGlobs=*Proposal*
wikihelp.source.archive.maxPages=200
```

An exact archived category URL can be supplied with `categoryUrl`.

## Local or Git-backed content

```properties
wikihelp.source.local.type=local
wikihelp.source.local.location=docs
wikihelp.source.local.language=en
wikihelp.source.local.include.tags=eclipse-help
wikihelp.source.local.include.pathGlobs=**/*.mediawiki,**/*.html
```

MediaWiki categories, front matter tags, and `<meta name="wikihelp-tags" content="...">` are recognized. A fixed `format` can be configured when file extensions do not identify the markup.

## Authentication and cache safety

Configuration stores only environment-variable names. Tokens and passwords are resolved at runtime and never written into page metadata or the cache. Remote URLs, page revisions, tags, and content are cached so that offline builds can reproduce the selected documentation set.
