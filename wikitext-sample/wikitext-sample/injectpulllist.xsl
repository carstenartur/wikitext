<?xml version="1.0" encoding="UTF-8"?>
<!-- Carsten Hammer carsten.hammer@t-online.de This stylesheet injects the 
	list of pages to pull into the ant build script -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="2.0" xmlns:categories="http://dummy/categories"
	exclude-result-prefixes="categories">
	<!-- These are all categories that are pulled from the mediawiki server
	<categories:category>Zest</categories:category> -->
	<categories:category>Eclipse4</categories:category>
	<categories:category>GEF</categories:category>
	<xsl:output method="xml" indent="yes" name="plugin"
		standalone="yes" xmlns:categories="http://dummy/categories"
		exclude-result-prefixes="categories" />
	<xsl:output method="xml" indent="yes" standalone="yes"
		xmlns:categories="http://dummy/categories" exclude-result-prefixes="categories" />
	<xsl:template match="/">
		<xsl:apply-templates select="node()"/>
		<!--  
		<xsl:variable name="categorylist" select="document('')/xsl:stylesheet/categories:category" />
		<xsl:variable name="filename" select="plugin_new.xml" />
		<xsl:result-document href="{$filename}" format="plugin">
			<plugin>
				<extension point="org.eclipse.help.toc">
					<xsl:for-each select="$categorylist">
						<toc primary="true">
							<xsl:attribute name="category"><xsl:value-of
								select="." /></xsl:attribute>
							<xsl:attribute name="extradir"><xsl:value-of
								select="." /></xsl:attribute>
							<xsl:attribute name="file"><xsl:value-of
								select="concat(.,'_toc.xml')" /></xsl:attribute>
						</toc>
					</xsl:for-each>
				</extension>
			</plugin>
		</xsl:result-document>
		-->
	</xsl:template>
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>
	<xsl:template match="/project/target[@name='pull']">
		<target name="pull">
			<xsl:for-each select="document('')/xsl:stylesheet/categories:category">
				<get>
					<xsl:attribute name="dest"><xsl:value-of
						select="concat('tmp/',replace(.,'é','e'),'_list.xml')" /></xsl:attribute>
					<xsl:attribute name="src"><xsl:value-of
						select="concat('http://wiki.eclipse.org/api.php?action=query&amp;list=categorymembers&amp;cmtitle=Category:',.,'&amp;cmlimit=150&amp;format=xml')" /></xsl:attribute>
				</get>
				<get>
					<xsl:attribute name="dest"><xsl:value-of
						select="concat('tmp/',replace(.,'é','e'),'_list_de.xml')" /></xsl:attribute>
					<xsl:attribute name="src"><xsl:value-of
						select="concat('http://wiki.eclipse.org/api.php?action=query&amp;list=categorymembers&amp;cmtitle=Category:',.,'/de&amp;cmlimit=150&amp;format=xml')" /></xsl:attribute>
				</get>
			</xsl:for-each>
		</target>
	</xsl:template>
	<xsl:template match="/project/target[@name='generate-online-help']">
		<target name="generate-online-help" description="Generate Eclipse help from mediawiki source">
			<xsl:for-each select="document('')/xsl:stylesheet/categories:category">
				<trycatch>
					<try>
						<mediawiki-to-eclipse-help wikiBaseUrl="http://wiki.eclipse.org/"
							validate="true" helpPrefix="nl/en" failonvalidationerror="true"
							prependImagePrefix="images" formatoutput="true"
							defaultAbsoluteLinkTarget="mylyn_external" linkRel=""
							multipleOutputFiles="false" internalLinkPattern="../{0}"
							generateUnifiedToc="true" navigationImages="false">
							<xsl:attribute name="dest">${basedir}/nl/en</xsl:attribute>
							<xsl:attribute name="title"><xsl:value-of
								select="." /></xsl:attribute>
							<xsl:attribute name="tocFile"><xsl:value-of
								select="concat('${basedir}/nl/en/',replace(.,'é','e'),'_toc.xml')" /></xsl:attribute>
							<stylesheet url="book.css" />
						</mediawiki-to-eclipse-help>
						<!-- we copy the english tocs to root - so we have the chapters for 
							the default language en -->
						<copy overwrite="true">
							<xsl:attribute name="file"><xsl:value-of
								select="concat('${basedir}/nl/en/',replace(.,'é','e'),'_toc.xml')" /></xsl:attribute>
							<xsl:attribute name="tofile"><xsl:value-of
								select="concat('${basedir}/',replace(.,'é','e'),'_toc.xml')" /></xsl:attribute>
						</copy>
					</try>
					<catch>
						<echo>Investigate exceptions in the run!</echo>
					</catch>
					<finally>
						<echo>In &lt;finally&gt;.</echo>
					</finally>
				</trycatch>
			</xsl:for-each>
		</target>
	</xsl:template>
</xsl:stylesheet>
