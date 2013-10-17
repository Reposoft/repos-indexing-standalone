/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import org.apache.solr.client.solrj.SolrServer;

/**
 * Provides Solr cores based on solr webapp root URL.
 * 
 * The reason for using a provider is that we could add providers that dynamically load cores.
 */
public class SolrCoreProviderAssumeExisting implements SolrCoreProvider {

	private String solrUrl;
	
	public SolrCoreProviderAssumeExisting(String solrUrl) {
		if (!solrUrl.endsWith("/")) {
			throw new IllegalArgumentException("Solr root URL must have trailing slash");
		}
		this.solrUrl = solrUrl;
	}
	
	/**
	 * @param coreName
	 * @return reuses the same instace for all provider calls
	 */
	@Override
	public SolrServer getSolrCore(String coreName) {
		String coreUrl = solrUrl + coreName;
		// provide useful toString, helps when there's multiple cores in the handler chain
		return new se.repos.indexing.solrj.HttpSolrServerNamed(coreUrl).setName(coreName);
	}

}
