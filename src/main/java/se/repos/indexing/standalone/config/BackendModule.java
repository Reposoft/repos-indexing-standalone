/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import org.tmatesoft.svn.core.io.SVNRepository;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.backend.svnkit.info.CmsRepositoryLookupSvnkit;
import se.simonsoft.cms.backend.svnkit.info.change.CmsChangesetReaderSvnkit;
import se.simonsoft.cms.backend.svnkit.info.change.CmsContentsReaderSvnkit;
import se.simonsoft.cms.backend.svnkit.info.change.CommitRevisionCache;
import se.simonsoft.cms.backend.svnkit.info.change.CommitRevisionCacheRepo;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsChangesetReader;
import se.simonsoft.cms.item.inspection.CmsContentsReader;

public class BackendModule extends AbstractModule {

	private CmsRepositorySvn repository;

	public BackendModule(CmsRepositorySvn repository) {
		this.repository = repository;
	}

	@Override
	protected void configure() {
		bind(CmsRepository.class).toInstance(repository);
		bind(CmsRepositorySvn.class).toInstance(repository);
		
		// no longer global, http communication will only be per-repo.
		bind(SVNRepository.class).toProvider(SvnKitRepositoryProvider.class);
		
		// Likely not possible to bind non-annotated CmsRepositoryLookup in combination with test framework.
		// Potentially remove annotation "inspection" after completed move from svnlook to http communication.
		//bind(CmsRepositoryLookup.class).to(CmsRepositoryLookupSvnkitLook.class);
		bind(CmsRepositoryLookup.class).annotatedWith(Names.named("inspection")).to(CmsRepositoryLookupSvnkit.class);
		
		bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkit.class);
		bind(CmsContentsReader.class).to(CmsContentsReaderSvnkit.class);
		
		bind(CommitRevisionCache.class).toInstance(new CommitRevisionCacheRepo()); // Bind an instance of the cache, per-repo.
	}
	
}
