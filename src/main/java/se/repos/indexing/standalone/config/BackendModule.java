package se.repos.indexing.standalone.config;

import org.tmatesoft.svn.core.wc.admin.SVNLookClient;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsChangesetReaderSvnkitLookRepo;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsContentsReaderSvnkitLookRepo;
import se.simonsoft.cms.backend.svnkit.svnlook.CmsRepositoryLookupSvnkitLook;
import se.simonsoft.cms.backend.svnkit.svnlook.CommitRevisionCache;
import se.simonsoft.cms.backend.svnkit.svnlook.CommitRevisionCacheDefault;
import se.simonsoft.cms.backend.svnkit.svnlook.SvnlookClientProviderStateless;
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
		
		bind(SVNLookClient.class).toProvider(SvnlookClientProviderStateless.class);
		
		bind(CmsRepositoryLookup.class).to(CmsRepositoryLookupSvnkitLook.class);
		// the old name distinguisher when mixed with user-level webapp, deprecated
		bind(CmsRepositoryLookup.class).annotatedWith(Names.named("inspection")).to(CmsRepositoryLookupSvnkitLook.class);
		
		bind(CmsChangesetReader.class).to(CmsChangesetReaderSvnkitLookRepo.class);
		bind(CmsContentsReader.class).to(CmsContentsReaderSvnkitLookRepo.class);
		
		bind(CommitRevisionCache.class).to(CommitRevisionCacheDefault.class);
	}
	
}
