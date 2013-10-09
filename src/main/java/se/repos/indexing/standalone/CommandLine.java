package se.repos.indexing.standalone;

import java.io.File;
import java.util.Date;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import se.repos.indexing.ReposIndexing;
import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.standalone.config.BackendModule;
import se.repos.indexing.standalone.config.IndexingHandlersModuleXml;
import se.repos.indexing.standalone.config.ParentModule;
import se.repos.indexing.standalone.config.IndexingModule;
import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.repos.indexing.standalone.config.SolrCoreProviderAssumeExisting;
import se.simonsoft.cms.backend.svnkit.CmsRepositorySvn;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;

public class CommandLine {

	public static void main(String[] args) {

		CommandOptions options = new CommandOptions();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
			// support hook style execution
			if (options.getRepository() == null) {
				if (options.getArguments().size() == 0) {
					throw new CmdLineException(parser, "Repository not set and no unnamed argument");
				}
				options.setRepository(new File(options.getArguments().get(0)));
			}
			if (options.getRepository() == null && options.getArguments().size() > 1) {
				options.setRevision(options.getArguments().get(1));
			}
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java -jar repos-indexing.jar [options...] arguments...");
			parser.printUsage(System.err);
			return;
		}
		
		File repo = options.getRepository();
		String url = options.getRepositoryUrl();
		if (url == null) {
			url = guessRepositoryUrl(repo);
			System.err.println("Warning: guessed repository url " + url);
		}
		CmsRepositorySvn repository = new CmsRepositorySvn(url, repo);

		SolrCoreProvider solrCoreProvider = new SolrCoreProviderAssumeExisting(options.getSolrUrl());
		
		System.out.println("Indexing repository " + repository + " from " + repo + " to " + options.getSolrUrl());
		
		Module parentModule = new ParentModule();
		Injector parent = Guice.createInjector(parentModule);
		Module backendModule = new BackendModule(repository);
		Module indexingModule = new IndexingModule(solrCoreProvider);
		Module indexingHandlersModule = new IndexingHandlersModuleXml();
		Injector repositoryContext = parent.createChildInjector(backendModule, indexingModule, indexingHandlersModule);
		
		IndexingSchedule schedule = repositoryContext.getInstance(IndexingSchedule.class);
		ReposIndexing indexing = repositoryContext.getInstance(ReposIndexing.class);
		if (options.getOperation() != CommandOptions.Operation.clear) {
			schedule.start();
		}
		
		CmsRepositoryLookup lookup = repositoryContext.getInstance(CmsRepositoryLookup.class);
		RepoRevision revision = getRevision(options.getRevision(), repository, lookup);
		
		indexing.sync(revision);
	}

	protected static RepoRevision getRevision(String optionsRevision, CmsRepositorySvn repository, CmsRepositoryLookup lookup) {
		if (optionsRevision == null) {
			return lookup.getYoungest(repository);
		} else {
			Long revisionNumber = Long.parseLong(optionsRevision); // TODO support other type of revisions or add parser in backend module
			Date revisionDate = lookup.getRevisionTimestamp(null, revisionNumber);
			return new RepoRevision(revisionNumber, revisionDate);
		}
	}

	protected static String guessRepositoryUrl(File repo) {
		return "http://localhost/svn/" + repo.getName();
	}

}
