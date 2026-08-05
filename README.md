# wikitext

A small Eclipse help plug-in generated from WikiText source material.

[![Java CI with Maven](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml)

## Current platform

The default target platform is **Eclipse 2026-06 (Eclipse Platform 4.40)**. The released repository is pinned in `wikitext-sample/rcptarget/rcptarget.target`, so builds remain reproducible instead of following a moving update site.

The build uses:

- JDK 21
- Maven 3.9.16 in CI (Tycho requires Maven 3.9.9 or newer)
- Eclipse Tycho 5.0.3

## Build

```bash
mvn -B --no-transfer-progress verify --file wikitext-sample/pom.xml
```

The generated plug-in is written below `wikitext-sample/wikitext-sample/target/`. Copy the generated `wikitext-sample-*.jar` to the Eclipse `dropins` directory, restart Eclipse, and open **Help > Help Contents > GEF**.

Historical target definitions remain packaged as classified target artifacts. To select and validate one explicitly, for example:

```bash
mvn -B \
  -Dtarget.classifier=2022-03 \
  -Dtarget.file=2022-03.target \
  verify --file wikitext-sample/pom.xml
```

## Change project version

```bash
mvn org.eclipse.tycho:tycho-versions-plugin:5.0.3:set-version \
  -DnewVersion=1.2.0 \
  --file wikitext-sample/pom.xml
```
