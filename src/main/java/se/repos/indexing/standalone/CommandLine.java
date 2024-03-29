/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.tmatesoft.svn.util.Version;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;

import se.repos.indexing.IndexAdmin;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.solrj.SolrOptimize;
import se.repos.indexing.standalone.CommandOptions.Operation;
import se.repos.indexing.standalone.config.BackendModule;
import se.repos.indexing.standalone.config.IndexingHandlersModuleXml;
import se.repos.indexing.standalone.config.IndexingModule;
import se.repos.indexing.standalone.config.ParentModule;
import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.repos.indexing.standalone.config.SolrCoreProviderAssumeExisting;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.version.CmsComponents;

public class CommandLine {

	static { // couldn't find how to fall back to defaults in log4j config param values
		if (System.getProperty("se.repos.loglevel") == null) {
			System.setProperty("se.repos.loglevel", "info");
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(CommandLine.class);
		
	public static void main(String[] args) throws InterruptedException {

		CommandOptions options = new CommandOptions();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
			
			if (options.getParentUrl() != null) {
				// daemon mode
				if (options.getArguments().size() == 0) {
					throw new CmdLineException(parser, "Daemon mode requires one or more repositories named as arguments.", null);
				}
			} else {
				// support hook style execution
				// TODO: verify after http transition, need to also support -p with local path?
				if (options.getArguments().size() != 1) {
					throw new CmdLineException(parser, "Non-daemon mode requires a single named repository as argument (corename for optimize).", null);
				}
			}
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java -jar repos-indexing.jar [options...] repositories...");
			parser.printUsage(System.err);
			return;
		}
		
		if (options.getLogLevel() != null) {
			throw new IllegalArgumentException("Log level argument is deprecated. Set -Dlog4j.configurationFile or -Dse.repos.loglevel=debug instead.");
		}
		
		logger.info("JVM version: {} {}", System.getProperty("java.vm.name"), System.getProperty("java.vm.version"));
		logger.info("JVM default charset: {}", Charset.defaultCharset());
		if (CmsComponents.logAllVersions() == 1) {
			// for bundled jar
			CmsComponents.logPomProperties("META-INF/maven/se.repos/repos-indexing/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.repos/repos-indexing-fulltext/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.simonsoft/cms-backend-svnkit/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.simonsoft/cms-indexing-keydef/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.simonsoft/cms-indexing-xml/pom.properties");
			
		}
		logger.info("Version Saxon: {}", net.sf.saxon.Version.getProductVersion());
		setupSvnkit(); // Logs the SVNKit version.
		
		SolrCoreProvider solrCoreProvider = new SolrCoreProviderAssumeExisting(options.getSolrUrl());
		
		if (options.getSVNPubSubUrl() != null && options.getParentUrl() != null) {
			logger.info("SVNPubSub: {}", options.getSVNPubSubUrl());
			try {
				runDaemonPubSub(options, solrCoreProvider);
			} catch (Throwable e) {
				logger.error("Terminating indexing service: {} ({})", e.getMessage(), e.toString(), e);
				System.exit(1);
			}
			logger.info("Terminating indexing service.");
			System.exit(0);
		}
		
		if (options.getParentUrl() != null) {
			try {
				runDaemon(options, solrCoreProvider);
			} catch (Throwable e) {
				logger.error("Terminating indexing service: {} ({})", e.getMessage(), e.toString(), e);
				System.exit(1);
			}
			logger.info("Terminating indexing service.");
			return;
		}
		

		String url = options.getRepositoryUrl();
		if (url == null) {
			url = guessRepositoryUrl(options.getArguments().get(0));
			if (options.getOperation() == Operation.clear) {
				logger.info("Guessed repository url {}. Should be insignificant at clear.", url);
			} else {
				logger.warn("Guessed repository url {}. Set using -u.", url);
			}
		}
		CmsRepositorySvn repository = new CmsRepositorySvn(url);
		
		Module parentModule = new ParentModule(solrCoreProvider);
		Injector parent = Guice.createInjector(parentModule);
		Module backendModule = new BackendModule(repository);
		Module indexingModule = new IndexingModule();
		Module indexingHandlersModule = new IndexingHandlersModuleXml();
		Injector repositoryContext = parent.createChildInjector(backendModule, indexingModule, indexingHandlersModule);
		
		if (options.getWaitInitial() != null) {
			Thread.sleep(options.getWaitInitial() * 1000);
		}
		
		// Clear and resync typically requires the URL via -u
		if (options.getOperation() == Operation.clear || options.getOperation() == Operation.resync) {
			if (options.getRepositoryUrl() == null) {
				logger.warn("Clear typically requires the repository URL (-u).");
			}
			repositoryContext.getInstance(IndexAdmin.class).clear();
		}
		if (options.getOperation() == Operation.clear) {
			return;
		}
		if (options.getOperation() == Operation.optimize) {
			List<String> cores = options.getArguments();
			for (String corename : cores) {
				logger.info("Optimizing core {}", corename);
				SolrClient core = solrCoreProvider.getSolrCore(corename);
				new SolrOptimize(core).run();
			}
			logger.info("Optimize completed");
			return;
		}
		
		IndexingSchedule schedule = repositoryContext.getInstance(IndexingSchedule.class);
		ReposIndexing indexing = repositoryContext.getInstance(ReposIndexing.class);
		if (options.getOperation() != CommandOptions.Operation.clear) {
			schedule.start();
		}
		
		CmsRepositoryLookup lookup = repositoryContext.getInstance(Key.get(CmsRepositoryLookup.class, Names.named("inspection")));
		RepoRevision revision = getRevision(options.getRevision(), repository, lookup);
		
		indexing.sync(revision);
	}

	private static void runDaemon(CommandOptions options, SolrCoreProvider solrCoreProvider) {
		IndexingDaemon d = new IndexingDaemon(options.getParentUrl(), options.getArguments(),
				solrCoreProvider);
		d.setWait(options.getWait() != null ? options.getWait() * 1000 : 0);
		d.run();
	}
	
	private static void runDaemonPubSub(CommandOptions options, SolrCoreProvider solrCoreProvider) {
		IndexingDaemon d = new IndexingDaemonPubSub(options.getParentUrl(), options.getArguments(),
				solrCoreProvider, options.getSVNPubSubUrl());
		d.setWait(options.getWait() != null ? options.getWait() * 1000 : 0);
		d.run();
	}

	protected static RepoRevision getRevision(String optionsRevision, CmsRepositorySvn repository, CmsRepositoryLookup lookup) {
		if (optionsRevision == null) {
			return lookup.getYoungest(repository);
		} else {
			long revisionNumber = Long.parseLong(optionsRevision); // TODO support other type of revisions or add parser in backend module
			Date revisionDate = lookup.getRevisionTimestamp(repository, revisionNumber);
			return new RepoRevision(revisionNumber, revisionDate);
		}
	}

	protected static String guessRepositoryUrl(String repoName) {
		return "http://localhost/svn/" + repoName;
	}
	
	private static void setupSvnkit() {
		
		// Currently no configuration, only accessing via svnlook.
		String version = Version.getMajorVersion() + "." + Version.getMinorVersion() + "." + Version.getMicroVersion();
		String revNumber = Version.getRevisionString();
		String verMsg = MessageFormatter.format("Version SVNKit: {}", new Object[] { version + " (r" + revNumber + ")" }).getMessage();
		logger.info(verMsg);
	}
	
}
