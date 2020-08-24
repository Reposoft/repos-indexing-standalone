/*
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.util.Set;

import se.repos.indexing.IndexingItemHandler;
import se.repos.indexing.item.IndexingItemProgress;
import se.simonsoft.cms.version.CmsComponents;

public class HandlerIndexingVersion implements IndexingItemHandler {

	private final String indexingVersion;
	
	public static final String INDEXING_VERSION_FIELD = "embd_repos-indexing-standalone_version"; // Using embd for now.
	
	public HandlerIndexingVersion() {
		this.indexingVersion = CmsComponents.getVersion("repos-indexing-standalone").getVersion();
	}

	@Override
	public Set<Class<? extends IndexingItemHandler>> getDependencies() {
		return null;
	}

	@Override
	public void handle(IndexingItemProgress progress) {
		progress.getFields().addField(INDEXING_VERSION_FIELD, indexingVersion);
	}

}
