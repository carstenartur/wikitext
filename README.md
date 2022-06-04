wikitext
========

wikitextsample

[![Java CI with Maven](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/wikitext/actions/workflows/maven.yml)


Test
========

mvn -Drcpversion=2022-03 package

Then copy wikitext-sample-1.0.0.jar to your eclipse "dropins" folder and start and check help->help contents->GEF

Change Version
========

mvn org.eclipse.tycho:tycho-versions-plugin:set-version -Drcpversion=2022-03 -DnewVersion=1.0.0
