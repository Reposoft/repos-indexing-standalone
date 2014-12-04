/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.fulltext.HandlerFulltext;
import se.simonsoft.cms.indexing.xml.IndexAdminXml;
import se.simonsoft.cms.indexing.xml.IndexingHandlersXml;
import se.simonsoft.cms.indexing.xml.XmlIndexFieldExtraction;
import se.simonsoft.cms.indexing.xml.XmlIndexWriter;
import se.simonsoft.cms.indexing.xml.custom.XmlMatchingFieldExtractionSource;
import se.simonsoft.cms.indexing.xml.custom.XmlMatchingFieldExtractionSourceDefault;
import se.simonsoft.cms.indexing.xml.solr.XmlIndexWriterSolrjBackground;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class IndexingHandlersModuleXml extends AbstractModule {

	@Override
	protected void configure() {
		bind(IndexAdminXml.class).asEagerSingleton();
		
		// ticket:821 The safe choice is XmlIndexWriterSolrj.class while XmlIndexWriterSolrjBackground.class provides 25-30% better performance.
		bind(XmlIndexWriter.class).to(XmlIndexWriterSolrjBackground.class);
		// No longer injecting the XmlSourceReader. It is hard coded to S9API. Must be able to use different one in Pretranslate.
		//bind(XmlSourceReader.class).to(XmlSourceReaderJdom.class);
		
		Multibinder<IndexingItemHandler> handlers = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
		IndexingHandlersXml.configureFirst(handlers);
		handlers.addBinding().to(HandlerFulltext.class);
		IndexingHandlersXml.configureLast(handlers);
		
		Multibinder<XmlIndexFieldExtraction> xmlExtraction = Multibinder.newSetBinder(binder(), XmlIndexFieldExtraction.class);
		IndexingHandlersXml.configureXmlFieldExtraction(xmlExtraction);
		
		bind(XmlMatchingFieldExtractionSource.class).to(XmlMatchingFieldExtractionSourceDefault.class);
	}

}
