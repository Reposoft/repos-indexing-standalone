/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import javax.inject.Provider;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

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
		return new HttpSolrServer(coreUrl);
	}

}
