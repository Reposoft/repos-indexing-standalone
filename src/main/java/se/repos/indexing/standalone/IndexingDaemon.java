/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.standalone.config.BackendModule;
import se.repos.indexing.standalone.config.IndexingHandlersModuleXml;
import se.repos.indexing.standalone.config.IndexingModule;
import se.repos.indexing.standalone.config.ParentModule;
import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;

public class IndexingDaemon implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private Injector global;
	
	private Map<CmsRepository, ReposIndexing> loaded = new LinkedHashMap<CmsRepository, ReposIndexing>();
	
	/**
	 * 
	 * @param parentPath
	 * @param parentUrl
	 * @param include Empty to discover and re-discover repositories
	 */
	public IndexingDaemon(File parentPath, String parentUrl, List<String> include, SolrCoreProvider solrCoreProvider) {
		
		if (!parentPath.exists()) {
			throw new IllegalArgumentException("Not found: " + parentPath);
		}
		
		if (include.size() == 0) {
			throw new IllegalArgumentException("Repository discovery not implemented");
		}
		
		global = getGlobal(solrCoreProvider);
		
		List<String> names = include;
		
		for (String name :  names) {
			File path = new File(parentPath, name);
			String url = parentUrl + name;
			add(path, url);
		}
	}
	
	protected void add(File path, String url) {
		CmsRepositorySvn repository = new CmsRepositorySvn(url, path);
		Injector context = getSvn(global, repository);
		ReposIndexing indexing = context.getInstance(ReposIndexing.class);
		loaded.put(repository, indexing);
		logger.info("Added repository {} for admin path {}", repository.getUrl(), repository.getAdminPath());
	}

	protected Injector getGlobal(SolrCoreProvider solrCoreProvider) {
		return Guice.createInjector(new ParentModule(solrCoreProvider));
	}
	
	protected Injector getSvn(Injector global, CmsRepositorySvn repository) {
		Module backendModule = new BackendModule(repository);
		Module indexingModule = new IndexingModule();
		Module indexingHandlersModule = new IndexingHandlersModuleXml();
		return global.createChildInjector(backendModule, indexingModule, indexingHandlersModule);		
	}

	@Override
	public void run() {
		
		CmsRepositoryLookup lookup = global.getInstance(CmsRepositoryLookup.class);
		
		IndexingSchedule schedule = global.getInstance(IndexingSchedule.class);
		schedule.start();
		
		long wait = 10000;
		
		while (true) {
			runOnce(lookup);
			logger.debug("Waiting for {} ms before next run", wait);
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				logger.debug("Interrupted");
				break; // abort
			}
		}
	}

	private void runOnce(CmsRepositoryLookup lookup) {
		for (CmsRepository repo : loaded.keySet()) {
			runOnce(lookup, repo);
		}
	}

	private void runOnce(CmsRepositoryLookup lookup, CmsRepository repo) {
		RepoRevision head = lookup.getYoungest(repo);
		ReposIndexing indexing = loaded.get(repo);
//		if (indexing.getRevision().equals(head)) {
//			logger.debug("Index for {} already at head {}", repo, head);
//			return;
//		}
		logger.debug("Sync {} {}", repo, head);
		indexing.sync(head);
	}
	
}
