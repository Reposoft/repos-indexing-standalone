/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import org.apache.solr.client.solrj.SolrClient;

public interface SolrCoreProvider {

	public SolrClient getSolrCore(String coreName);
	
}
