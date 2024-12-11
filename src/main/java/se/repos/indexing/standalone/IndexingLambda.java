package se.repos.indexing.standalone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.standalone.config.BackendModule;
import se.repos.indexing.standalone.config.IndexingHandlersModuleXml;
import se.repos.indexing.standalone.config.IndexingModule;
import se.repos.indexing.standalone.config.ParentModule;
import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.repos.indexing.standalone.config.SolrCoreProviderAssumeExisting;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsContentsReader;

import java.util.List;

public class IndexingLambda implements RequestHandler<List<Integer>, Integer> {

	private static Boolean inited = init();

	// Current cms-indexing-lambda has env: CMS_CLOUDID, CMS_SOLR_URL 
	// Likely add CMS_SVN_URL 
	
	private static String solrUrl = "http://localhost:8983/solr/";
	private static String svnUrl = "http://localhost:8983/svn/demo1";
	
	private static SolrCoreProvider solrCoreProvider = new SolrCoreProviderAssumeExisting(solrUrl);
	private static Injector global = getGlobal(solrCoreProvider);
	private static Injector context = getSvn(global, new CmsRepositorySvn(svnUrl));
	private static ReposIndexing indexing = context.getInstance(ReposIndexing.class);
	private static CmsRepositoryLookup lookup = context.getInstance(Key.get(CmsRepositoryLookup.class, Names.named("inspection")));
	private static CmsContentsReader contents = context.getInstance(CmsContentsReader.class);
	
	private static Injector getGlobal(SolrCoreProvider solrCoreProvider) {
		return Guice.createInjector(new ParentModule(solrCoreProvider));
	}
	
	private static  Injector getSvn(Injector global, CmsRepositorySvn repository) {
		Module backendModule = new BackendModule(repository);
		Module indexingModule = new IndexingModule();
		Module indexingHandlersModule = new IndexingHandlersModuleXml();
		return global.createChildInjector(backendModule, indexingModule, indexingHandlersModule);		
	}
	
	@Override
	/*
	 * Takes a list of Integers and returns its sum.
	 */
	public Integer handleRequest(List<Integer> event, Context context) {
		System.out.println("Indexing... out");
		System.err.println("Indexing... err");

		System.out.println("Indexing... " + global.getInstance(IndexingSchedule.class));
		System.out.println("Indexing... " + indexing);
		System.out.println("Indexing... " + lookup);
		System.out.println("Indexing... " + contents);

		
		
		LambdaLogger logger = context.getLogger();
		logger.log("EVENT TYPE: " + event.getClass().toString());
		return event.stream().mapToInt(Integer::intValue).sum();
	}
	
	
	
	private static Boolean init() {
		System.out.println("Indexing init... out");
		System.err.println("Indexing init... err");
		return Boolean.TRUE;
	}
}
