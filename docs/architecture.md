# Architecture

## Design goals

WikiHelp keeps four concerns independent:

1. **Discovery** finds candidate pages through source-native mechanisms such as categories, labels, CQL, explicit page IDs, or archive links.
2. **Selection** applies one common model of tags, page names, title globs, and path globs.
3. **Synchronization** stores normalized pages in a deterministic cache that can be versioned.
4. **Rendering** creates Eclipse Help without knowing which remote system supplied a page.

This separation prevents a retired API, a changed authentication method, or a new documentation store from forcing changes to the Eclipse Help generator.

## Modules

| Module | Responsibility |
|---|---|
| `wikihelp-core` | Page model, selectors, HTTP/JSON support, source SPI, cache |
| `wikihelp-source-local` | Local files and Git working trees |
| `wikihelp-source-mediawiki` | MediaWiki Action API |
| `wikihelp-source-gitlab` | GitLab project Wiki API |
| `wikihelp-source-confluence` | Confluence CQL and rendered pages |
| `wikihelp-source-eclipsepedia` | Static Eclipsepedia archive |
| `wikihelp-renderer-eclipse-help` | HTML preparation, TOC, WikiText Ant render plan |
| `wikihelp-cli` | Pipeline orchestration and build integration |
| `wikitext-sample` | Example Eclipse Help plug-in |

New source systems implement `org.hammer.wikihelp.core.WikiSource` and register the implementation through `ServiceLoader`.

## Normalized page model

Every adapter returns a `WikiPage` containing:

- stable source and remote IDs
- title and logical path
- language and markup format
- source or rendered content
- normalized tags
- original URI and revision

The cache stores this metadata and content without credentials. Page order, tag order, and file names are deterministic.

## Selection semantics

Include dimensions are combined with **AND**:

- included tags
- included titles
- included paths
- explicit pages

Values within one dimension are combined with **OR**, except when `tags.mode=all`. Exclusions are applied afterward. Separate source blocks can express unions of substantially different selector sets.

## Build modes

- `online`: always query remote sources and replace their cache
- `cached`: use existing cache; populate missing remote caches
- `offline`: never query remote sources and fail clearly when a required cache is missing

Local sources are always read directly because they are already part of the build input. Their transient cache supports the same downstream pipeline.

## Rendering

HTML pages are sanitized, relative links are resolved against the source URI, and the result is wrapped as an Eclipse Help page. MediaWiki, Confluence, Textile, TracWiki, and TWiki source pages are grouped by language and markup format and rendered with Mylyn WikiText. The renderer then writes one master Eclipse Help TOC.
