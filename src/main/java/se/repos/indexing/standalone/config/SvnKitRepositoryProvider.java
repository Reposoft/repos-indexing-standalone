/**
 * Copyright (C) 2009-2017 Simonsoft Nordic AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import se.simonsoft.cms.item.CmsRepository;

/**
 * Provides the non thread-safe SVNRepository,
 * configured with separate port and no authentication.
 */
public class SvnKitRepositoryProvider implements Provider<SVNRepository> {

	private SVNURL repositoryRootUrl;
	public static boolean httpV2Enabled = true;

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
	}
	
	@Override
	public SVNRepository get() {
		return getNewSvnRepository(repositoryRootUrl);
	}

	protected SVNRepository getNewSvnRepository(SVNURL rootUrl) {
		SVNRepository file;
		try {
			file = SVNRepositoryFactory.create(rootUrl);
		} catch (SVNException e) {
			throw new RuntimeException("Failed to initialize SvnKit repository for URL " + rootUrl);
		}
		// #938 Enable SVNKit HttpV2 for users of the low-level provider.
		if (httpV2Enabled && file instanceof DAVRepository) {
			DAVRepository dav = (DAVRepository) file; 
			dav.setHttpV2Enabled(true);
			logger.debug("Enabled HttpV2 support in DAVRepository instance.");
		}
		return file;
	}
	
}
