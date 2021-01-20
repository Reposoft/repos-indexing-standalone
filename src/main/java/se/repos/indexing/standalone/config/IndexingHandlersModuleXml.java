/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.s9api.Processor;
import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.fulltext.HandlerFulltext;
import se.repos.indexing.standalone.HandlerIndexingVersion;
import se.simonsoft.cms.indexing.abx.HandlerCategory;
import se.simonsoft.cms.indexing.abx.HandlerClassification;
import se.simonsoft.cms.indexing.abx.HandlerTitleSelection;
import se.simonsoft.cms.indexing.keydef.HandlerKeydefExcel;
import se.simonsoft.cms.indexing.keydef.HandlerKeydefXliff;
import se.simonsoft.cms.indexing.keydef.HandlerTransformTika;
import se.simonsoft.cms.indexing.xml.IndexAdminXml;
import se.simonsoft.cms.indexing.xml.IndexingHandlersXml;
import se.simonsoft.cms.indexing.xml.XmlIndexFieldExtraction;
import se.simonsoft.cms.indexing.xml.XmlIndexWriter;
import se.simonsoft.cms.indexing.xml.custom.IndexFieldExtractionCustomXsl;
import se.simonsoft.cms.indexing.xml.custom.XmlMatchingFieldExtractionSource;
import se.simonsoft.cms.indexing.xml.custom.XmlMatchingFieldExtractionSourceDefault;
import se.simonsoft.cms.indexing.xml.solr.XmlIndexWriterSolrjBackground;
import se.simonsoft.cms.xmlsource.SaxonConfiguration;
import se.simonsoft.cms.xmlsource.handler.XmlSourceReader;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.function.GetChecksum;
import se.simonsoft.cms.xmlsource.transform.function.GetPegRev;
import se.simonsoft.cms.xmlsource.transform.function.WithPegRev;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class IndexingHandlersModuleXml extends AbstractModule {

	public static final String CONFIG_XML_MAX_FILESIZE = "se.simonsoft.cms.indexing.xml.maxFilesize";
	public static final int CONFIG_XML_MAX_FILESIZE_DEFAULT = 10 * 1024*1024; // Raised default to 10M (from 3M).
	
	public static final String CONFIG_XML_SUPPRESSRIDBEFORE = "se.simonsoft.cms.indexing.xml.suppressRidBefore";
	
	@Override
	protected void configure() {
		bind(Processor.class).toProvider(SaxonConfiguration.class);
		Multibinder<ExtensionFunctionDefinition> transformerFunctions = Multibinder.newSetBinder(binder(), ExtensionFunctionDefinition.class);
		transformerFunctions.addBinding().to(GetChecksum.class);
		transformerFunctions.addBinding().to(GetPegRev.class);
		transformerFunctions.addBinding().to(WithPegRev.class);
		bind(XmlSourceReader.class).to(XmlSourceReaderS9api.class);
		
		MapBinder<String, Source> sourceBinder = MapBinder.newMapBinder(binder(), String.class, Source.class);
		sourceBinder.addBinding("identity.xsl").toInstance(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/xmlsource/transform/identity.xsl")));
		sourceBinder.addBinding("source-reuse.xsl").toInstance(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/xmlsource/transform/source-reuse.xsl")));
		
		// ticket:821 The safe choice is XmlIndexWriterSolrj.class while XmlIndexWriterSolrjBackground.class provides 25-30% better performance.
		bind(XmlIndexWriter.class).to(XmlIndexWriterSolrjBackground.class);
		
		// Item indexing, add XML handler
		Multibinder<IndexingItemHandler> handlers = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
		IndexingHandlersXml.configureFirst(handlers);
		handlers.addBinding().to(HandlerIndexingVersion.class);
		handlers.addBinding().to(HandlerFulltext.class);
		handlers.addBinding().to(HandlerKeydefXliff.class);
		handlers.addBinding().to(HandlerKeydefExcel.class);
		handlers.addBinding().to(HandlerTransformTika.class);
		handlers.addBinding().to(HandlerTitleSelection.class);
		handlers.addBinding().to(HandlerClassification.class);
		handlers.addBinding().to(HandlerCategory.class);
		IndexingHandlersXml.configureLast(handlers);
		
		// XML field extraction
		Multibinder<XmlIndexFieldExtraction> xmlExtraction = Multibinder.newSetBinder(binder(), XmlIndexFieldExtraction.class);
		IndexingHandlersXml.configureXmlFieldExtraction(xmlExtraction);
		
		// Used in field extraction. We don't have a strategy yet for placement of the custom xsl, read from jar
		bind(XmlMatchingFieldExtractionSource.class).to(XmlMatchingFieldExtractionSourceDefault.class);
		bind(IndexFieldExtractionCustomXsl.class).asEagerSingleton(); // This line was not present before 0.16, but was in the testconfig.
		
		// hook into repos-indexing actions
		bind(IndexAdminXml.class).asEagerSingleton();
		
		// Type support is what's stopping us from extracting a method for this
		Integer maxFilesize = CONFIG_XML_MAX_FILESIZE_DEFAULT;
		String maxFilesizeStr = System.getProperty(CONFIG_XML_MAX_FILESIZE);
		if (maxFilesizeStr != null) {
			maxFilesize = Integer.parseInt(maxFilesizeStr);
		}
		bind(Integer.class).annotatedWith(Names.named(CONFIG_XML_MAX_FILESIZE)).toInstance(maxFilesize);
		
		String suppressRidBefore = System.getProperty(CONFIG_XML_SUPPRESSRIDBEFORE);
		if (suppressRidBefore == null) {
			suppressRidBefore = "";
		}
		bind(String.class).annotatedWith(Names.named(CONFIG_XML_SUPPRESSRIDBEFORE)).toInstance(suppressRidBefore);
	}

}
