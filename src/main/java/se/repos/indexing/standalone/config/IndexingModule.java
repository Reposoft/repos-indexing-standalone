/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import se.repos.indexing.IdStrategy;
import se.repos.indexing.IndexAdmin;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.item.IdStrategyDefault;
import se.repos.indexing.item.ItemContentBufferStrategy;
import se.repos.indexing.item.ItemPropertiesBufferStrategy;
import se.repos.indexing.repository.IndexAdminPerRepositoryRepositem;
import se.repos.indexing.repository.ReposIndexingPerRepository;
import se.repos.indexing.twophases.ItemContentsMemorySizeLimit;
import se.repos.indexing.twophases.ItemPropertiesImmediate;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class IndexingModule extends AbstractModule {
	
	@Override
	protected void configure() {
		bind(IndexAdmin.class).to(IndexAdminPerRepositoryRepositem.class);
		bind(ReposIndexing.class).to(ReposIndexingPerRepository.class);
		
		bind(IdStrategy.class).to(IdStrategyDefault.class);
		
		bind(Integer.class).annotatedWith(Names.named("indexingFilesizeInMemoryLimitBytes")).toInstance(10 * 1024 * 1024);
		bind(ItemContentBufferStrategy.class).to(ItemContentsMemorySizeLimit.class);
		bind(ItemPropertiesBufferStrategy.class).to(ItemPropertiesImmediate.class);
	}

}
