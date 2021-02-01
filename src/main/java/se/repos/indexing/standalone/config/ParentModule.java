/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import org.apache.solr.client.solrj.SolrClient;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.scheduling.IndexingScheduleBlockingOnly;

public class ParentModule extends AbstractModule {

	private SolrCoreProvider solrCoreProvider;

	public ParentModule(SolrCoreProvider solrCoreProvider) {
		this.solrCoreProvider = solrCoreProvider;
	}

	@Override
	protected void configure() {
		for (String core : new String[]{"repositem", "reposxml"}) {
			bind(SolrClient.class).annotatedWith(Names.named(core)).toInstance(solrCoreProvider.getSolrCore(core));
		}
		bind(IndexingSchedule.class).to(IndexingScheduleBlockingOnly.class);
		// this is an all-inspection context
		/* #919 Preparing for replacing svnlook with http. No communication in global context.
		bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		bind(CmsRepositoryLookup.class).to(CmsRepositoryLookupSvnkitLook.class);
		*/
	}
	
}
