<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<!-- The param language determine the destination language folder -->
	<xsl:param name="language" />

	<xsl:template match="/">
		<toc label="Zest">
			<topic label="Zest">
				<xsl:apply-templates select="@*" />
				<xsl:for-each
					select="document(concat('nl/', $language,'/tmp_zest_toc.xml'))/toc/topic">
					<topic>
						<xsl:variable name="lastname" select="tokenize(./@label, '\.')[last()]" />
						<xsl:attribute name="href"><xsl:value-of
							select="./@href" /></xsl:attribute>
						<xsl:attribute name="label"><xsl:value-of
							select="$lastname" /></xsl:attribute>
						<xsl:apply-templates select="*" />
					</topic>
				</xsl:for-each>

			</topic>
		</toc>
	</xsl:template>

	<xsl:template match="node()|*">
		<topic>
			<xsl:attribute name="href"><xsl:value-of select="./@href" /></xsl:attribute>
			<xsl:attribute name="label"><xsl:value-of select="./@label" /></xsl:attribute>
			<xsl:apply-templates select="*" />
		</topic>
	</xsl:template>
</xsl:stylesheet>