/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.io.File;
import java.util.Date;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import se.repos.indexing.IndexAdmin;
import se.repos.indexing.ReposIndexing;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.standalone.CommandOptions.Operation;
import se.repos.indexing.standalone.config.BackendModule;
import se.repos.indexing.standalone.config.IndexingHandlersModuleXml;
import se.repos.indexing.standalone.config.ParentModule;
import se.repos.indexing.standalone.config.IndexingModule;
import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.repos.indexing.standalone.config.SolrCoreProviderAssumeExisting;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.version.CmsComponents;

public class CommandLine {

	public static void main(String[] args) {

		CommandOptions options = new CommandOptions();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
			// support hook style execution
			if (options.getParentPath() != null) {
				if (options.getParentUrl() == null) {
					throw new CmdLineException(parser, "Daemon mode requires parent URL");
				}
			} else if (options.getRepository() == null) {
				if (options.getArguments().size() == 0) {
					throw new CmdLineException(parser, "Repository not set and no unnamed argument");
				}
				options.setRepository(new File(options.getArguments().get(0)));
			}
			if (options.getRepository() != null && options.getRevision() == null && options.getArguments().size() > 1) {
				options.setRevision(options.getArguments().get(1));
			}
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java -jar repos-indexing.jar [options...] arguments...");
			parser.printUsage(System.err);
			return;
		}
		
		if (CmsComponents.logAllVersions() == 1) {
			// for bundled jar
			CmsComponents.logPomProperties("META-INF/maven/se.repos/repos-indexing/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.repos/repos-indexing-fulltext/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.simonsoft/cms-indexing/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.simonsoft/cms-indexing-xml/pom.properties");
			CmsComponents.logPomProperties("META-INF/maven/se.simonsoft/cms-backend-svnkit/pom.properties");
		}
		
		SolrCoreProvider solrCoreProvider = new SolrCoreProviderAssumeExisting(options.getSolrUrl());
		
		if (options.getParentPath() != null) {
			runDaemon(options, solrCoreProvider);
			return;
		}
		
		File repo = options.getRepository();
		String url = options.getRepositoryUrl();
		if (url == null) {
			url = guessRepositoryUrl(repo);
			System.err.println("Warning: guessed repository url " + url);
		}
		CmsRepositorySvn repository = new CmsRepositorySvn(url, repo);
		
		Module parentModule = new ParentModule(solrCoreProvider);
		Injector parent = Guice.createInjector(parentModule);
		Module backendModule = new BackendModule(repository);
		Module indexingModule = new IndexingModule();
		Module indexingHandlersModule = new IndexingHandlersModuleXml();
		Injector repositoryContext = parent.createChildInjector(backendModule, indexingModule, indexingHandlersModule);
		
		if (options.getOperation() == Operation.clear || options.getOperation() == Operation.resync) {
			repositoryContext.getInstance(IndexAdmin.class).clear();
		}
		if (options.getOperation() == Operation.clear) {
			return;
		}
		
		IndexingSchedule schedule = repositoryContext.getInstance(IndexingSchedule.class);
		ReposIndexing indexing = repositoryContext.getInstance(ReposIndexing.class);
		if (options.getOperation() != CommandOptions.Operation.clear) {
			schedule.start();
		}
		
		CmsRepositoryLookup lookup = repositoryContext.getInstance(CmsRepositoryLookup.class);
		RepoRevision revision = getRevision(options.getRevision(), repository, lookup);
		
		indexing.sync(revision);
	}

	private static void runDaemon(CommandOptions options, SolrCoreProvider solrCoreProvider) {
		new IndexingDaemon(options.getParentPath(), options.getParentUrl(), options.getArguments(),
				solrCoreProvider).run();
	}

	protected static RepoRevision getRevision(String optionsRevision, CmsRepositorySvn repository, CmsRepositoryLookup lookup) {
		if (optionsRevision == null) {
			return lookup.getYoungest(repository);
		} else {
			Long revisionNumber = Long.parseLong(optionsRevision); // TODO support other type of revisions or add parser in backend module
			Date revisionDate = lookup.getRevisionTimestamp(repository, revisionNumber);
			return new RepoRevision(revisionNumber, revisionDate);
		}
	}

	protected static String guessRepositoryUrl(File repo) {
		return "http://localhost/svn/" + repo.getName();
	}

}
