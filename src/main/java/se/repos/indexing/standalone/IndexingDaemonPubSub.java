/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.io.File;
import java.nio.channels.NonWritableChannelException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.media.sse.EventListener;
import org.glassfish.jersey.media.sse.EventSource;
import org.glassfish.jersey.media.sse.SseFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;

public class IndexingDaemonPubSub extends IndexingDaemon {

	private static final Logger logger = LoggerFactory.getLogger(IndexingDaemonPubSub.class);
	
	public static final long WAIT_PUBSUB_DEFAULT = 60000; // Interval btw verifying that the pubsub channel is up.
	
	protected long waitCurrent = WAIT_PUBSUB_DEFAULT;

	private final CmsRepositoryLookup lookup;
	private final String url;
	private final WebTarget target;

	private Date stillAlive;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private final Set<Future<CmsRepository>> executorFutures = new LinkedHashSet<>();

	public IndexingDaemonPubSub(File parentPath, String parentUrl, List<String> include, SolrCoreProvider solrCoreProvider, String url) {
		super(parentPath, parentUrl, include, solrCoreProvider);
		this.url = url;

		lookup = global.getInstance(CmsRepositoryLookup.class);
		
		logger.debug("Preparing EventSource");
		Client client = ClientBuilder.newBuilder().register(SseFeature.class).build();
		target = client.target(this.url);
	}

	private Boolean isEventSourceAlive() {
		
		int stale = 45;
		
		if (this.stillAlive == null) {
			// Still starting up.
			return null;
		}
		
		Date now = new Date();
		
		if ((this.stillAlive.getTime() - now.getTime()) > stale*1000) {
			logger.warn("PubSub has been silent for >{} seconds.", stale);
			return false;
		}
		return true;
	}
	
	private EventSource startEventSource() {

		logger.debug("Starting EventSource...");
		// Testing a simple GET can point out issues related to dependencies.
		/*
		logger.info("Making simple GET request...");
		Response get = target.request().get();
		logger.info("Made simple GET request: {}", get);
		//int status = get.
		*/

		EventSource eventSource = EventSource.target(target).build();
		stillAlive = null;
		
		EventListener listenerAlive = inboundEvent -> {
			logger.info("stillalive event: {}", inboundEvent.readData());
			stillAlive = new Date();
		};
		eventSource.register(listenerAlive, "stillalive");
		
		EventListener listenerConnected = inboundEvent -> {
			logger.info("connected event: {}", inboundEvent.readData());
			stillAlive = new Date();
		};
		eventSource.register(listenerConnected, "svnpubsub");

		EventListener listenerCommit = inboundEvent -> {
			if (!"commit".equals(inboundEvent.getName())) {
				throw new IllegalStateException("Listener for commit event received: " + inboundEvent.getName());
			}

			logger.info("commit event: {}", inboundEvent.readData());
			// Not really interested in the JSON data, just triggering sync of all repositories.
			// When repo is svnsynced the revprops might not have been set yet when indexing starts.
			// Empty committer revprop could be an indicator.
			for (CmsRepository repo : loaded.keySet()) {
				syncRepo(lookup, repo);
			}
		};
		eventSource.register(listenerCommit, "commit");

		EventListener listenerAny = inboundEvent -> logger.debug("Event {}", inboundEvent.getName());
		eventSource.register(listenerAny);

		eventSource.open();
		logger.info("Opened EventSource");
		return eventSource;
	}
	
	

	@Override
	public void run() {

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
		
		EventSource eventSource = startEventSource();
		while (true) {
			// Must always sync at least once when starting up.
			logger.debug("Performing periodic sync.");
			for (CmsRepository repo : loaded.keySet()) {
				syncRepo(lookup, repo);
			}

			Boolean isAlive = isEventSourceAlive();
			if (isAlive == null) {
				logger.info("PubSub not confirmed alive.");
			} else if (isAlive) {
				waitCurrent = WAIT_PUBSUB_DEFAULT;
			} else {
				logger.warn("PubSub connection might be down, reconnecting.");
				waitCurrent = wait; // Use the configured wait interval until PubSub is back up.
				eventSource.close();
				eventSource = startEventSource(); // Start new connection.
			}
			
			if (waitCurrent == 0) {
				break;
			}
			/*
			if (runs > 0) {
				logger.info("Waiting {} ms between PubSub validations", waitCurrent);
			} else {
				logger.trace("Waiting {} ms before next PubSub validation", waitCurrent);
			}
			*/
			try {
				Thread.sleep(waitCurrent);
			} catch (InterruptedException e) {
				logger.debug("Interrupted");
				break; // abort
			}
			
			// Inspect previous sync executions and terminate if one has failed.
			// This mimics the single-thread behavior of the classic IndexingDaemon.
			inspectFutures();
		}

		eventSource.close();
		logger.info("Closed EventSource");
		executorService.shutdownNow();
	}

	
	protected void syncRepo(CmsRepositoryLookup lookup, CmsRepository repo) {
		
		Future<CmsRepository> future = executorService.submit(new RepositorySyncCallable(lookup, repo));
		logger.info("Submitted sync for repository '{}'", repo.getName());
		executorFutures.add(future);
	}
	
	private void inspectFutures() {
		
		for (Iterator<Future<CmsRepository>> i = executorFutures.iterator(); i.hasNext(); ) {
			Future<CmsRepository> future = i.next();
            try {
            	if (future.isDone()) {
            		CmsRepository repo = future.get();
            		i.remove();
            		logger.debug("Completed sync for repository '{}'", repo.getName());
            	}
            } catch (InterruptedException e) {
                logger.warn("Interrupted while inspecting execution future: {}", future, e);
            } catch (ExecutionException e) {
            	// Already logged with details.
            	logger.debug("Sync failed: ", e);
            	throw new RuntimeException("Sync execution failed.", e);
            }
        }
	}
	
	class RepositorySyncCallable implements Callable<CmsRepository>{

		private final CmsRepositoryLookup lookup;
		private final CmsRepository repo;
		
		RepositorySyncCallable(CmsRepositoryLookup lookup, CmsRepository repo) {
			this.lookup = lookup;
			this.repo = repo;
		}
		
		@Override
		public CmsRepository call() throws Exception {
			
			logger.info("Starting sync for repository '{}'", repo.getName());
			
			try {
				runOnce(lookup, repo);
			} catch (NonWritableChannelException e) {
				
				logger.info("SVNKit failed svnlook youngest, sleep and retry.");
				Thread.sleep(200);
				syncRepo(lookup, repo); // Retry added last in queue.
			} catch (Exception e) {
				logger.error("Sync failed for repository '{}'", repo.getName(), e);
				throw e;
			} catch (Error error) {
				logger.error("Sync failed with Error for repository '{}'", repo.getName(), error);
				throw error;
			}
			return repo;
		}
	}
}
