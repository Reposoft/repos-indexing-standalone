/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	
	private long wait = WAIT_DEFAULT;
	
	private Injector global;
	
	private Map<File, CmsRepository> known = new HashMap<File, CmsRepository>();
	private Map<CmsRepository, ReposIndexing> loaded = new LinkedHashMap<CmsRepository, ReposIndexing>();
	private Map<CmsRepository, RepoRevision> previous = new HashMap<CmsRepository, RepoRevision>();
	private Map<CmsRepository, CmsContentsReader> contentsReaders = new HashMap<CmsRepository, CmsContentsReader>();
	private List<CmsRepository> reposToRemove = null;
	
	private boolean discovery = false;
	
	private FileFilter discoveryFilter = new DiscoveryFilterDefault();

	private File parentPath;

	private String parentUrl;
	
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
		if (!parentUrl.endsWith("/")) {
			throw new IllegalArgumentException("Parent path URL must end with slash");
		}
		this.parentPath = parentPath;
		this.parentUrl = parentUrl;
		
		global = getGlobal(solrCoreProvider);
		
		if (include.size() == 0) {
			setDiscovery(true);
			logger.info("Discovery enabled at {} with parent URL {}", parentPath, parentUrl);
		} else {
			List<String> names = include;
			for (String name :  names) {
				File path = new File(parentPath, name);
				String url = parentUrl + name;
				addRepository(path, url);
			}
		}
	}
	
	public void setDiscovery(boolean enable) {
		if (enable == false) {
			this.discovery = false;
		}
		setDiscovery(new DiscoveryFilterDefault());
	}

	public void setDiscovery(FileFilter discoveryFilter) {
		this.discovery = true;
		this.discoveryFilter = discoveryFilter;
	}	
	
	public boolean isDiscoveryEnabled() {
		return discovery;
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
				known.remove(repo);
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

	protected void discover() {
		File[] folders = parentPath.listFiles(discoveryFilter);
		for (File f : folders) {
			String name = f.getName();
			String url = parentUrl + name;
			if (!known.containsKey(f)) {
				logger.info("Discovered repository folder {}, named {}", f, name);
				addRepository(f, url);
			}
		}
	}
	
	/**
	 * Runs all repositories.
	 */
	@Override
	public void run() {
		
		CmsRepositoryLookup lookup = global.getInstance(CmsRepositoryLookup.class);
		
		IndexingSchedule schedule = global.getInstance(IndexingSchedule.class);
		schedule.start();
		
		try { // Mostly for keeping old indentation.
			if (discovery) {
				discover();
			}
			// #198 Performing the evaluation of indexing:mode early during startup to make it possible to inspect the log.
			Set<CmsRepository> removeRepos = new HashSet<CmsRepository>();
			for (CmsRepository repo : loaded.keySet()) {
				if (!indexingEnabled(repo)) {
					removeRepos.add(repo);
				}
			}
			// Actually remove them, avoiding concurrent modification.
			for (CmsRepository repo : removeRepos) {
				removeRepository(repo);
			}
		} catch (Exception e) {
			logger.error("Discovery failed: {}", e.getMessage(), e);
			throw new RuntimeException("Discovery failed.", e);
		}
		
		logger.info("Indexing enabled for repositories: {}", loaded.keySet());
		while (true) {
			int runs = 0;
			reposToRemove = new ArrayList<CmsRepository>();
			
			for (CmsRepository repo : loaded.keySet()) {
				runs += runOnce(lookup, repo) ? 1 : 0;
			}
			
			logger.debug("Will remove {} repos", reposToRemove.size());
			for (CmsRepository repo: reposToRemove) {
				logger.debug("Removing deleted repository {}", repo);
				removeRepository(repo);
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
	private boolean runOnce(CmsRepositoryLookup lookup, CmsRepository repo) {
		RepoRevision head;
		try {
			head = lookup.getYoungest(repo);
		} catch (RuntimeException e) {
			if (isStillExisting(repo)) {
				throw e;
			}
			logger.info("Repository {} not found", repo);
			logger.warn("Removing repository {} but not its index contents", repo.getName());
			reposToRemove.add(repo);
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
	
	private boolean indexingEnabled(CmsRepository repo) {
		
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

	public static class DiscoveryFilterDefault implements FileFilter {

		public static String[] SKIP_SUFFIX = new String[] {".old", ".org", ".noindex"};

		private Set<File> skipped = new HashSet<File>();
		
		@Override
		public boolean accept(File file) {
			if (!file.isDirectory()) {
				return false;
			}
			for (String skip : SKIP_SUFFIX) {
				if (file.getName().contains(skip)) {
					if (skipped.add(file)) {
						logger.info("Skipping potential repository folder {} because name contains {}", file, skip);
					}
					return false;
				}
			}
			if (!new File(file, "format").exists()) {
				if (skipped.add(file)) {
					logger.info("Skipping potential repository folder {} because it doesn't look like a recognized repository format", file);
				}
				return false;
			}
			return true;
		}
		
	}
	
}
