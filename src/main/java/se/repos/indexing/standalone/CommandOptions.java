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
		clear
	};
	
	@Option(name="-s",
			usage="Solr URL, assuming multicore, for example http://localhost:8983/solr/")
	private String solrUrl = "http://localhost:8080/solr/";

	@Option(name="-u",
    		usage="Repository URL used for index IDs, for example http://www.where-we-work.com/svn/repo"
    		+ "\n If not set hostname resolution will be attempted using InetAddress.getLocalHost().getHostName()"
    		+ "\n and if found the parent path will default to /svn/"
    		+ "\n Custom IdStrategy impls could allow indexing without repository URL, but indexing has URL fields and has not been tested for that")
    private String repositoryUrl = null;

	@Option(name="-o",
			usage="Operation. Default is sync, supported values:"
    		+ "\n 'resync' - clear and sync, to HEAD if second argument is omitted"
    		+ "\n 'clear' - clear, first argument can be a repository path or omitted if -u is set")
    private Operation operation = Operation.sync;
	
	@Option(name="-p",
			usage="Repository path. If omitted this can be the first anonymous argument.")
    private File repository = null;
	
	@Option(name="-r",
			usage="Revision to sync to. If omitted this can be the second anonymous argument. Defaults to head.")
	private String revision = null;
	
    // receives other command line parameters than options
    @Argument
    private List<String> arguments = new LinkedList<String>();	
	
    public String getSolrUrl() {
		return solrUrl;
	}    
    
    public String getRepositoryUrl() {
		return repositoryUrl;
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
    
	public List<String> getArguments() {
		return arguments;
	}

}
