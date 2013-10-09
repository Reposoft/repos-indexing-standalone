package se.repos.indexing.standalone.config;

import se.repos.indexing.item.IndexingItemHandler;
import se.simonsoft.cms.indexing.xml.IndexingHandlersXml;
import se.simonsoft.cms.indexing.xml.XmlIndexFieldExtraction;
import se.simonsoft.cms.indexing.xml.XmlIndexWriter;
import se.simonsoft.cms.indexing.xml.solr.XmlIndexWriterSolrj;
import se.simonsoft.xmltracking.source.XmlSourceReader;
import se.simonsoft.xmltracking.source.jdom.XmlSourceReaderJdom;
import se.simonsoft.xmltracking.source.saxon.XmlMatchingFieldExtractionSource;
import se.simonsoft.xmltracking.source.saxon.XmlMatchingFieldExtractionSourceDefault;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class IndexingHandlersModuleXml extends AbstractModule {

	@Override
	protected void configure() {
		bind(XmlIndexWriter.class).to(XmlIndexWriterSolrj.class);
		bind(XmlSourceReader.class).to(XmlSourceReaderJdom.class);
		
		Multibinder<IndexingItemHandler> handlers = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
		IndexingHandlersXml.configureFirst(handlers);
		IndexingHandlersXml.configureLast(handlers);
		
		Multibinder<XmlIndexFieldExtraction> xmlExtraction = Multibinder.newSetBinder(binder(), XmlIndexFieldExtraction.class);
		IndexingHandlersXml.configureXmlFieldExtraction(xmlExtraction);
		
		bind(XmlMatchingFieldExtractionSource.class).to(XmlMatchingFieldExtractionSourceDefault.class);
	}

}
