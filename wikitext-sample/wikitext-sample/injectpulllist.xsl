<?xml version="1.0" encoding="UTF-8"?>
<!-- 
Carsten Hammer
carsten.hammer@t-online.de

This stylesheet injects the list of pages to pull into the ant build script
 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0" xmlns:categories="http://dummy/categories" exclude-result-prefixes="categories">
	<!-- These are all categories that are pulled from the mediawiki server -->
	<categories:category>Zest</categories:category>
	<categories:category>Eclipse4</categories:category>
	<xsl:output method="xml" indent="yes" standalone="yes" xmlns:categories="http://dummy/categories" exclude-result-prefixes="categories"/>
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()"/>
		</xsl:copy>
	</xsl:template>
	<xsl:template match="/project/target[@name='pull']">
		<target name="pull">
			<xsl:for-each select="document('')/xsl:stylesheet/categories:category">
				<get>
					<xsl:attribute name="dest"><xsl:value-of select="concat('tmp/',replace(.,'é','e'),'_list.xml')"/></xsl:attribute>
					<xsl:attribute name="src"><xsl:value-of select="concat('http://wiki.eclipse.org/api.php?action=query&amp;list=categorymembers&amp;cmtitle=Category:',.,'&amp;cmlimit=150&amp;format=xml')"/></xsl:attribute>
				</get>
				<get>
					<xsl:attribute name="dest"><xsl:value-of select="concat('tmp/',replace(.,'é','e'),'_list_de.xml')"/></xsl:attribute>
					<xsl:attribute name="src"><xsl:value-of select="concat('http://wiki.eclipse.org/api.php?action=query&amp;list=categorymembers&amp;cmtitle=Category:',.,'/de&amp;cmlimit=150&amp;format=xml')"/></xsl:attribute>
				</get>
			</xsl:for-each>
		</target>
	</xsl:template>
	<xsl:template match="mediawiki-to-eclipse-help/path">
	</xsl:template>
</xsl:stylesheet>
