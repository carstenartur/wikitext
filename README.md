# WikiHelp

WikiHelp selects documentation pages from wiki systems and Git-backed source trees, normalizes them, downloads referenced images and attachments, and generates an installable Eclipse Help plug-in. Selection is metadata-driven: MediaWiki categories, Confluence labels, GitLab/local front matter tags, archived Eclipsepedia categories, explicit page names, and title or path globs can all decide which pages enter the help system.

[![Java CI with Maven](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml)

## Sources

The source SPI currently provides adapters for:

- **MediaWiki** through the Action API, including category discovery, source or rendered content, and image resolution through `imageinfo`
- **GitLab Wiki** through the project Wiki API, with rendered HTML by default, tags read from page front matter, and authenticated `/uploads/` assets
- **Confluence Cloud or Data Center** through CQL, labels, rendered `export_view` content, and authenticated attachment downloads
- **Eclipsepedia** through its read-only static HTML archive, including archived images and downloadable files
- **Local/Git content** including MediaWiki, Confluence, Textile, TracWiki, TWiki, HTML, Markdown, and AsciiDoc files with in-tree assets

The Eclipse Help renderer accepts HTML directly and delegates MediaWiki, Confluence, Textile, TracWiki, and TWiki source markup to Mylyn WikiText. Sources such as GitLab Markdown are normally requested as rendered HTML, so their native server renderer remains authoritative.

## Architecture

```text
Wiki source adapters
        │
        ▼
WikiPage + WikiAttachment records
        │
        ├── deterministic page and binary-asset cache
        │
        ▼
Eclipse Help renderer
        │
        ├── HTML sanitized and links localized
        ├── WikiText render plan
        └── deduplicated local assets
        ▼
Eclipse Help plug-in
```

Source acquisition, page selection, attachment synchronization, caching, and rendering are separate modules. A normal build can therefore remain offline and reproducible, while an explicit online synchronization refreshes remote pages and their assets.

See [Architecture](docs/architecture.md) and [Configuration](docs/configuration.md).

## Current build platform

- Eclipse 2026-06 / Eclipse Platform 4.40
- JDK 21
- Eclipse Tycho 5.0.3
- Maven 3.9.16 in CI

The released Eclipse repository is pinned in `wikitext-sample/rcptarget/rcptarget.target`.

## Build the checked-in sample

```bash
mvn -B --no-transfer-progress verify --file wikitext-sample/pom.xml
```

The sample reads tagged MediaWiki files from a local Git-backed source directory and packages the generated output as `wikitext-sample/wikitext-sample/target/wikitext-sample-1.1.0-SNAPSHOT.jar`.

## Synchronize remote sources

Configure a remote adapter in `wikitext-sample/wikitext-sample/wikihelp.properties`, then populate a persistent cache:

```bash
mvn -B \
  -Dwikihelp.mode=online \
  -Dwikihelp.cache=src-cache \
  verify --file wikitext-sample/pom.xml
```

Commit the cache when the help build must be reproducible without network access. Pages, attachment metadata, and binary assets are stored below the source cache. Later builds use:

```bash
mvn -B \
  -Dwikihelp.mode=offline \
  -Dwikihelp.cache=src-cache \
  verify --file wikitext-sample/pom.xml
```

Credentials are read only from environment variables named in the source configuration; they are never stored in the cache manifest. Per-attachment size and count limits protect synchronization from unexpectedly large downloads.

## Historical Eclipse targets

Historical target definitions remain packaged as classified target artifacts. For example:

```bash
mvn -B \
  -Dtarget.classifier=2022-03 \
  -Dtarget.file=2022-03.target \
  verify --file wikitext-sample/pom.xml
```
