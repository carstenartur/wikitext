<?xml version="1.0" encoding="UTF-8"?>
<!-- -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="2.0" xmlns:categories="http://dummy/categories"
	exclude-result-prefixes="categories">


	<xsl:output method="xml" indent="yes" standalone="yes"
		xmlns:categories="http://dummy/categories" exclude-result-prefixes="categories" />
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>

	<xsl:template
		match="/project/target[@name='generate-online-help' or @name='generate-online-help-de']/mediawiki-to-eclipse-help">
		<xsl:copy>
			<xsl:apply-templates select="@*" />
			<xsl:variable name="filename" select="replace(@title,'_de','')" />
			<xsl:variable name="enrichedfilename"
				select="replace(concat('tmp/',$filename,'_list',if(ends-with(@title,'_de')) then '_de' else '' ,'.xml'),' ','_')" />
			<xsl:variable name="categorytitle" select="@title"></xsl:variable>
			<xsl:message select="$enrichedfilename" />
			<xsl:for-each
				select="document($enrichedfilename)/api/query/categorymembers/cm">

				<path>
					<xsl:attribute name="name"><xsl:value-of
						select="./@title" /></xsl:attribute>
					<xsl:choose>
						<xsl:when test="contains(./@title,'/de')">
							<xsl:attribute name="title"><xsl:value-of
								select="substring-before(./@title,'/de')" /></xsl:attribute>
						</xsl:when>
						<xsl:otherwise>
							<xsl:attribute name="title"><xsl:value-of
								select="./@title" /></xsl:attribute>
						</xsl:otherwise>
					</xsl:choose>
				</path>
			</xsl:for-each>
			<xsl:apply-templates select="node()" />
		</xsl:copy>
	</xsl:template>
	<xsl:template match="mediawiki-to-eclipse-help/path">
	</xsl:template>
</xsl:stylesheet>
