# wikitext

A small Eclipse help plug-in generated from WikiText source material.

[![Java CI with Maven](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml)

## Current platform

The default target platform is **Eclipse 2026-06 (Eclipse Platform 4.40)**. The repository URL is pinned in `wikitext-sample/rcptarget/latest.target`, so builds remain reproducible instead of following a moving update site.

The build uses:

- JDK 21
- Maven 3.9.16 in CI (Tycho requires Maven 3.9.9 or newer)
- Eclipse Tycho 5.0.3

## Build

```bash
mvn -B --no-transfer-progress verify --file wikitext-sample/pom.xml
```

The generated plug-in is written below `wikitext-sample/wikitext-sample/target/`. Copy the generated `wikitext-sample-*.jar` to the Eclipse `dropins` directory, restart Eclipse, and open **Help > Help Contents > GEF**.

Historical target definitions remain available for compatibility checks. Select one explicitly, for example:

```bash
mvn -B -Drcpversion=2022-03 verify --file wikitext-sample/pom.xml
```

## Change project version

```bash
mvn org.eclipse.tycho:tycho-versions-plugin:5.0.3:set-version \
  -DnewVersion=1.2.0 \
  --file wikitext-sample/pom.xml
```
