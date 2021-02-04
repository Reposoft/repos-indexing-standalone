/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import javax.inject.Inject;
import javax.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepository;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;

import se.simonsoft.cms.item.CmsRepository;

/**
 * Provides the non thread-safe SVNRepository,
 * configured with separate port and no authentication.
 */
public class SvnKitRepositoryProvider implements Provider<SVNRepository> {

	private SVNURL repositoryRootUrl;
	public static boolean httpV2Enabled = true;
	private final ISVNRepositoryPool pool;

	static {
		// Needs to be done once
		DAVRepositoryFactory.setup();
	}

	private final Logger logger = LoggerFactory.getLogger(SvnKitRepositoryProvider.class);
	
	/**
	 * @param repository this is a per-repository service
	 * TODO: Consider injecting the port. 
	 */
	@Inject
	public SvnKitRepositoryProvider(CmsRepository repository) {
		try {
			SVNURL repoUrl = SVNURL.parseURIEncoded(repository.getUrl());
			if (repoUrl.hasPort()) {
				throw new IllegalArgumentException("CmsRepository may not specify a custom port: " + repository);
			}
			this.repositoryRootUrl = SVNURL.create("http", null, repoUrl.getHost(), 8091, repoUrl.getURIEncodedPath(), true);
		} catch (SVNException e) {
			throw new IllegalArgumentException("Not a valid repository: " + repository, e);
		}
		this.pool = new DefaultSVNRepositoryPool(null, null);
	}
	
	@Override
	public SVNRepository get() {
		return getNewSvnRepository(repositoryRootUrl);
	}

	protected SVNRepository getNewSvnRepository(SVNURL rootUrl) {
		SVNRepository file;
		try {
			//file = SVNRepositoryFactory.create(rootUrl);
			file = pool.createRepository(rootUrl, true);
		} catch (SVNException e) {
			throw new RuntimeException("Failed to initialize SvnKit repository for URL " + rootUrl);
		}
		// #938 Enable SVNKit HttpV2 for users of the low-level provider.
		if (httpV2Enabled && file instanceof DAVRepository) {
			DAVRepository dav = (DAVRepository) file; 
			if (!dav.isHttpV2Enabled()) {
				dav.setHttpV2Enabled(true);
				logger.info("Enabled HttpV2 support in DAVRepository instance: {}", rootUrl);
			}
		}
		return file;
	}
	
}
