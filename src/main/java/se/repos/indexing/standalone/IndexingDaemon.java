/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.io.File;
import java.nio.channels.NonWritableChannelException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

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
import se.simonsoft.cms.item.inspection.CmsContentsReader;
import se.simonsoft.cms.item.properties.CmsItemProperties;

public class IndexingDaemon implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(IndexingDaemon.class);

	public static final long WAIT_DEFAULT = 10000;
	
	protected long wait = WAIT_DEFAULT;
	
	protected Injector global;
	
	protected Map<File, CmsRepository> known = new HashMap<>();
	protected SortedMap<CmsRepository, ReposIndexing> loaded = new TreeMap<>(new CmsRepositoryComparator());
	protected Map<CmsRepository, RepoRevision> previous = new HashMap<>();
	protected Map<CmsRepository, CmsContentsReader> contentsReaders = new HashMap<>();

	protected File parentPath;

	protected String parentUrl;
	
	/**
	 * 
	 * @param parentPath SVN parent path
	 * @param parentUrl Equivalent URL including the trailing slash
	 * @param include Repository names to be included when indexing
	 */
	public IndexingDaemon(File parentPath, String parentUrl, List<String> include, SolrCoreProvider solrCoreProvider) {
		
		if (!parentPath.exists()) {
			throw new IllegalArgumentException("Not found: " + parentPath);
		}
		if (!parentUrl.endsWith("/")) {
			throw new IllegalArgumentException("Parent path URL must end with slash");
		}
		this.parentPath = parentPath;
		this.parentUrl = parentUrl;
		
		global = getGlobal(solrCoreProvider);
		
		if (include.size() == 0) {
			throw new IllegalArgumentException("At least one or more repositories have to be supplied");
		} else {
			for (String name : include) {
				File path = new File(parentPath, name);
				String url = parentUrl + name;
				addRepository(path, url);
			}
		}
	}

	/**
	 * @param wait btw polls in milliseconds
	 */
	public void setWait(long wait) {
		this.wait = wait;
	}
	
	public long getWait() {
		return this.wait;
	}
	
	// For testing
	Set<CmsRepository> getRepositoriesLoaded() {
		return this.loaded.keySet();
	}
	

	protected void addRepository(File path, String url) {
		if (known.containsKey(path)) {
			throw new IllegalArgumentException("Repository " + path + " already added");
		}
		CmsRepositorySvn repository = new CmsRepositorySvn(url, path);
		Injector context = getSvn(global, repository);
		ReposIndexing indexing = context.getInstance(ReposIndexing.class);
		loaded.put(repository, indexing);
		CmsContentsReader contents = context.getInstance(CmsContentsReader.class);
		contentsReaders.put(repository, contents);
		logger.info("Added repository {} for admin path {}", repository.getUrl(), repository.getAdminPath());
		known.put(path, repository);
	}
	
	protected void removeRepository(CmsRepository repo) {
		loaded.remove(repo);
		previous.remove(repo);
		for (File r : known.keySet()) {
			if (repo.equals(known.get(r))) {
				known.remove(r);
			}
		}
		logger.info("Removed repository {}", repo.getUrl());
	}
	
	protected boolean isStillExisting(CmsRepository repo) {
		if (repo instanceof CmsRepositorySvn) {
			return ((CmsRepositorySvn) repo).getAdminPath().exists();
		}
		logger.info("Can not determine if repository {} still exists, unknown type {}", repo, repo.getClass().getName());
		return true;
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

	/**
	 * Runs all repositories.
	 */
	@Override
	public void run() {
		
		CmsRepositoryLookup lookup = global.getInstance(CmsRepositoryLookup.class);
		
		IndexingSchedule schedule = global.getInstance(IndexingSchedule.class);
		schedule.start();
		
		// #198 Performing the evaluation of indexing:mode early during startup to make it possible to inspect the log.
		Set<CmsRepository> removeRepos = new HashSet<>();
		for (CmsRepository repo : loaded.keySet()) {
			if (!indexingEnabled(repo)) {
				removeRepos.add(repo);
			}
		}
		// Actually remove them, avoiding concurrent modification.
		for (CmsRepository repo : removeRepos) {
			removeRepository(repo);
		}

		logger.info("Indexing enabled for repositories: {}", loaded.keySet());
		while (true) {
			int runs = 0;
			for (CmsRepository repo : loaded.keySet()) {
				runs += runOnce(lookup, repo) ? 1 : 0;
			}

			if (wait == 0) {
				break;
			}
			if (runs > 0) {
				logger.info("Waiting {} ms intervals between runs", wait);
			} else {
				logger.trace("Waiting for {} ms before next run", wait);
			}
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				logger.debug("Interrupted");
				break; // abort
			}
		}
		
	}

	/**
	 * Run single repository
	 * @param lookup
	 * @param repo
	 */
	protected boolean runOnce(CmsRepositoryLookup lookup, CmsRepository repo) {
		RepoRevision head;
		try {
			head = lookup.getYoungest(repo);
		} catch (NonWritableChannelException e) {
			// Thrown by SVNKit 1.8.14 when called directly after post-commit hook.
			logger.error("SVNKit failed svnlook youngest.");
			logger.debug("SVNKit failed svnlook youngest: ", e);
			
			// Awaiting resolution in SVNKit or migration to http instead of svnlook.
			throw e;
			
		} catch (RuntimeException e) {
			if (isStillExisting(repo)) {
				logger.error("Failed to lookup youngest revision: ", e);
				throw e;
			}
			logger.warn("Repository {} not found", repo.getName());
			return false;
		}
		if (head.equals(previous.get(repo))) {
			logger.trace("Still at revision {} for repository {}", head, repo);
			return false;
		}
		previous.put(repo, head);
		ReposIndexing indexing = loaded.get(repo);
//		if (indexing.getRevision().equals(head)) {
//			logger.debug("Index for {} already at head {}", repo, head);
//			return;
//		}
		logger.debug("Sync {} {}", repo, head);
		indexing.sync(head);
		return true;
	}
	
	protected boolean indexingEnabled(CmsRepository repo) {
		
		CmsContentsReader contentsReader = contentsReaders.get(repo);
		if (contentsReader != null) {
			CmsItemProperties revProps = contentsReader.getRevisionProperties(new RepoRevision(0, null));
			logger.debug("{} revision props r0: {}", repo.getName(), (revProps != null ? revProps.getKeySet() : "null"));
			if (revProps != null && revProps.getString("indexing:mode") != null && "none".equals(revProps.getString("indexing:mode").trim())) {
				logger.warn("Indexing disabled for {}, indexing:mode was set to none.", repo);
				return false;
			}
		} else {
			logger.warn("Could not read properties for {}, no contents reader was loaded.", repo);
		}
		// Falling back to true even when unable to read revprops.
		return true;
	}

	static class CmsRepositoryComparator implements java.util.Comparator<CmsRepository> {
		@Override
		public int compare(CmsRepository o1, CmsRepository o2) {
			if (o1 == null) throw new IllegalArgumentException("CmsRepository can not be null");
			if (o2 == null) throw new IllegalArgumentException("CmsRepository can not be null");
			if (o1.getName() == null) throw new IllegalArgumentException("CmsRepository must have name " + o1);
			if (o2.getName() == null) throw new IllegalArgumentException("CmsRepository must have name " + o2);
			return o1.getName().compareTo(o2.getName());
		}
	}
	
}
