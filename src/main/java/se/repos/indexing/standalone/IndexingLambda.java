package se.repos.indexing.standalone;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
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
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.inspection.CmsContentsReader;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexingLambda implements RequestHandler<List<Integer>, Integer> {

	private static final Logger logger = LoggerFactory.getLogger(IndexingLambda.class);
			
	// Current cms-indexing-lambda has env: CMS_CLOUDID, CMS_SOLR_URL 
	// Add CMS_SVN_URL pointing to parent (no port) 
	// Add CMS_REPO - possibly Multi-value in the future unless we make multiple lambdas / SQS
	
	private static String cloudid = System.getenv().getOrDefault("CMS_CLOUDID", "demo1"); // Default only for local testing
	private static String repoName = System.getenv().getOrDefault("CMS_REPO", cloudid); // Defaults to cloudid
	private static String solrUrl = System.getenv().getOrDefault("CMS_SOLR_URL", "http://localhost:8983/solr/");;
	// CmsRepository may not specify a custom port (provider uses 8091)
	private static String svnParentUrl = System.getenv().getOrDefault("CMS_SVN_URL", "http://localhost/svn/");
	
	private static CmsRepositorySvn repo = new CmsRepositorySvn(svnParentUrl + repoName);
	private static SolrCoreProvider solrCoreProvider = new SolrCoreProviderAssumeExisting(solrUrl);
	private static Injector global = getGlobal(solrCoreProvider);
	private static Injector context = getSvn(global, repo);
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

		// TODO: https://stackoverflow.com/questions/46080448/logging-in-aws-lambda-with-slf4j
		/*
		LambdaLogger logger = context.getLogger();
		logger.log("EVENT TYPE: " + event.getClass().toString());
		*/
		
		logger.info("Starting sync for repository '{}'", repo.getName());
		
		System.out.println("Starting sync for repository: " + repo.getName());
		try {
			runOnce(lookup, repo);

		} catch (ProvisionException e) {
			// Workaround for bug in Guice.
			Field field = null;
			Set<?> value = Set.of("");
			try {
				field = e.getClass().getDeclaredField("messages");
				field.setAccessible(true);
				value = (Set<?>) field.get(e);
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}    
			System.out.println("Dependency injection failed: " + value.iterator().next());
			
		} catch (Exception e) {
			System.out.println("Failed: " + e.getClass());
			logger.error("Sync failed for repository '{}'", repo.getName(), e);
			throw e;
		} catch (Error error) {
			// Catch errors, like OutOfMemoryError and classloader issues.
			// Ensure that the JVM terminates instead of starting the next Callable in queue.
			try {
				logger.error("Sync failed with Error for repository '{}'", repo.getName(), error);
			} finally {
				System.exit(-10);
			}
			//throw error;
		}

		
		return event.stream().mapToInt(Integer::intValue).sum();
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
		} catch (RuntimeException e) {
			logger.warn("Repository {} not found", repo.getName());
			//return false;
			throw e;
		}

		logger.debug("Sync {} {}", repo, head);
		indexing.sync(head);
		return true;
	}
	
	
}
