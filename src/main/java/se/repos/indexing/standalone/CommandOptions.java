/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class CommandOptions {

	public enum Operation {
		sync,
		resync,
		clear,
		optimize
	};
	
	@Option(name="-s",
			usage="Solr URL, assuming multicore, for example http://localhost:8983/solr/")
	private String solrUrl = "http://localhost:8080/solr/";

	@Option(name="-u",
    		usage="Repository URL used for index IDs, for example http://www.where-we-work.com/svn/repo."
    		+ " If not set hostname resolution will be attempted using InetAddress.getLocalHost().getHostName() and if found the parent path will default to /svn/."
    		+ " Custom IdStrategy impls could allow indexing without repository URL, but indexing has URL fields and has not been tested for that.")
    private String repositoryUrl = null;
	
	@Option(name="--svnpubsub",
    		usage="SVNPubSub URL, for example http://localhost:2069/commits.")
    private String svnpubsubUrl = null;

	@Option(name="-o",
			usage="Operation. Default is sync, supported values:"
    		+ "\n'resync' - clear and sync, to HEAD if second argument is omitted"
    		+ "\n'clear' - clear, first argument can be a repository path or omitted if -u is set"
    		+ "\n'optimize' - run optimize on core names given by arguments, then exit")
    private Operation operation = Operation.sync;
	
	@Option(name="-p",
			usage="Repository path. If omitted this can be the first anonymous argument.")
    private File repository = null;
	
	@Option(name="-r",
			usage="Revision to sync to. If omitted this can be the second anonymous argument. Defaults to head.")
	private String revision = null;
	
	@Option(name="-w",
			usage="Wait a number of seconds between each repository poll. Defaults to 10.")
	private Long wait = 10L;
	
	@Option(name="--waitinitial",
			usage="Wait a number of seconds before initiating processing.")
	private Long waitInitial = null;

	// daemon mode
	@Option(name="-d",
			usage="Daemon mode, svn parent path. Unnamed arguments list the repository names to include, all if no unnamed args.")
	private File parentPath = null;
	
	@Option(name="-e",
			usage="Equivalent URL, for daemon mode, the url that corresponds to the parent path, with trailing slash.")
	private String parentUrl = null;
	
	@Option(name="-x",
			usage="Custom log level. Deprecated. Use -Dlog4j.configurationFile or -Dse.repos.loglevel=level instead.")
	private String logLevel = null;
	
    // receives other command line parameters than options
    @Argument
    private List<String> arguments = new LinkedList<String>();	
	
    public String getSolrUrl() {
		return solrUrl;
	}    
    
    public String getRepositoryUrl() {
		return repositoryUrl;
	}
    
    public String getSVNPubSubUrl() {
    	return svnpubsubUrl;
    }

	public Operation getOperation() {
		return operation;
	}

	public File getRepository() {
		return repository;
	}
	
	void setRepository(File repository) {
		this.repository = repository;
	}

	public String getRevision() {
		return revision;
	}
	
	void setRevision(String revision) {
		this.revision = revision;
	}
    
	public Long getWait() {
		return this.wait;
	}
	
	void setWait(Long wait) {
		this.wait = wait;
	}
	
	public Long getWaitInitial() {
		return this.waitInitial;
	}

	public void setWaitInitial(Long waitInitial) {
		this.waitInitial = waitInitial;
	}
	
	public File getParentPath() {
		return parentPath;
	}

	public String getParentUrl() {
		return parentUrl;
	}

	public String getLogLevel() {
		return logLevel;
	}
	
	public List<String> getArguments() {
		return arguments;
	}

}
