/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import se.simonsoft.cms.backend.svnkit.config.SvnKitLowLevelProviderThreadLocal;
import se.simonsoft.cms.item.CmsRepository;

/**
 * Provides the non thread-safe SVNRepository,
 * configured with separate port and no authentication.
 */
@Singleton // in each per-repo context
public class SvnKitRepositoryProvider extends SvnKitLowLevelProviderThreadLocal {

	/**
	 * @param repository this is a per-repository service
	 * TODO: Consider injecting the port. 
	 */
	@Inject
	public SvnKitRepositoryProvider(CmsRepository repository) {
		super(getRepositoryUrlConfiguredPort(repository), null);
	}
	
	private static SVNURL getRepositoryUrlConfiguredPort(CmsRepository repository) {
		try {
			SVNURL repoUrl = SVNURL.parseURIEncoded(repository.getUrl());
			if (repoUrl.hasPort()) {
				throw new IllegalArgumentException("CmsRepository may not specify a custom port: " + repository);
			}
			return SVNURL.create("http", null, repoUrl.getHost(), 8091, repoUrl.getURIEncodedPath(), true);
		} catch (SVNException e) {
			throw new IllegalArgumentException("Not a valid repository: " + repository, e);
		}
	}
	
}
