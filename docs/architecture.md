# Architecture

## Design goals

WikiHelp keeps five concerns independent:

1. **Discovery** finds candidate pages through source-native mechanisms such as categories, labels, CQL, explicit page IDs, or archive links.
2. **Selection** applies one common model of tags, page names, title globs, and path globs.
3. **Attachment synchronization** discovers source-native and embedded assets and retrieves them as bounded binary data.
4. **Caching** stores normalized pages and assets in a deterministic, versionable representation.
5. **Rendering** creates Eclipse Help without knowing which remote system supplied a page or attachment.

This separation prevents a retired API, a changed authentication method, or a new documentation store from forcing changes to the Eclipse Help generator.

## Modules

| Module | Responsibility |
|---|---|
| `wikihelp-core` | Page and attachment model, selectors, HTTP/JSON support, source SPI, cache |
| `wikihelp-source-local` | Local files and Git working trees, restricted to in-tree file assets |
| `wikihelp-source-mediawiki` | MediaWiki Action API and `imageinfo` resolution |
| `wikihelp-source-gitlab` | GitLab project Wiki API and authenticated Wiki uploads |
| `wikihelp-source-confluence` | Confluence CQL, rendered pages, and authenticated attachments |
| `wikihelp-source-eclipsepedia` | Static Eclipsepedia archive and archived assets |
| `wikihelp-renderer-eclipse-help` | HTML preparation, asset localization, TOC, WikiText Ant render plan |
| `wikihelp-cli` | Pipeline orchestration and build integration |
| `wikitext-sample` | Example Eclipse Help plug-in |

New source systems implement `org.hammer.wikihelp.core.WikiSource` and register the implementation through `ServiceLoader`. The SPI has defaults for embedded HTML, CSS, Markdown, AsciiDoc, and local MediaWiki references; an adapter can add native discovery or attachment authentication.

## Normalized model

Every adapter returns a `WikiPage` containing:

- stable source and remote IDs
- title and logical path
- language and markup format
- source or rendered content
- normalized tags
- original URI and revision
- zero or more `WikiAttachment` values

An attachment contains its reference as written in the page, resolved original URI, safe file name, MIME type, immutable binary content, size, and SHA-256 checksum. Binary equality is content-aware rather than array-identity based.

## Selection semantics

Include dimensions are combined with **AND**:

- included tags
- included titles
- included paths
- explicit pages

Values within one dimension are combined with **OR**, except when `tags.mode=all`. Exclusions are applied afterward. Separate source blocks can express unions of substantially different selector sets.

## Attachment pipeline

After page selection, each source receives the selected page and can return `AttachmentRequest` values. The common discovery layer recognizes:

- HTML `src`, `srcset`, `poster`, `data`, attachment-like `href`, and CSS `url(...)`
- Markdown images and attachment links
- AsciiDoc image and link macros
- local MediaWiki `File:` and `Image:` references

MediaWiki source pages additionally resolve file titles through the Action API `imageinfo` property. GitLab and Confluence reuse their page API authentication for protected assets. Eclipsepedia uses ordinary archive URLs.

`HttpTransport` downloads assets as bytes, follows redirects, rejects unsupported URI schemes, and enforces a maximum size before and while streaming. Local file references are normalized and must remain inside the configured source tree. Failed downloads either stop the synchronization or remain external, depending on configuration.

## Cache format

Cache version 2 stores:

```text
<cache>/<source>/manifest.tsv
<cache>/<source>/pages/*
<cache>/<source>/attachments.tsv
<cache>/<source>/assets/*
```

Page content remains UTF-8 text. Binary assets are named and deduplicated by SHA-256; attachment manifest rows associate references and source URIs with a page and cached asset. Checksums are verified when the cache is loaded, and normalized-path checks prevent manifest entries from escaping their cache directory.

Version 1 page-only caches remain readable. The next online or local synchronization rewrites them as version 2.

## Build modes

- `online`: always query remote sources and replace their page and attachment cache
- `cached`: use existing cache; populate missing remote caches
- `offline`: never query remote sources and fail clearly when a required cache is missing

Local sources are always read directly because they are already part of the build input. Their transient cache supports the same downstream pipeline.

## Rendering

HTML pages are sanitized. References matching downloaded attachments are rewritten to local `assets/` paths; other relative links are resolved against the page's source URI. `srcset` descriptors and CSS URLs are preserved while their URLs are localized.

MediaWiki, Confluence, Textile, TracWiki, and TWiki source pages are grouped by language and markup format and rendered with Mylyn WikiText. MediaWiki file references are converted to local image elements before WikiText runs. Assets with identical bytes are written only once per language. The renderer then writes one master Eclipse Help TOC.
