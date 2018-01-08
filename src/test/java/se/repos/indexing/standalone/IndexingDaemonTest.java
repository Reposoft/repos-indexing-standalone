package se.repos.indexing.standalone;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrServer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import se.repos.indexing.standalone.config.SolrCoreProvider;
import se.simonsoft.cms.item.CmsRepository;

public class IndexingDaemonTest {

	SolrCoreProvider solrCoreProvider;
	SolrServer solrServer;
	
	
	@Before
	public void initMocks() {
		solrServer = Mockito.mock(SolrServer.class);
		solrCoreProvider = Mockito.mock(SolrCoreProvider.class);
		when(solrCoreProvider.getSolrCore(Mockito.anyString())).thenReturn(solrServer);
	}
	
	@Test
	public void testRepositoryOrder() {
		
		List<String> repositories = Arrays.asList("repo1", "repo2", "demo1");
		
		IndexingDaemon d = new IndexingDaemon(new File("/tmp"), "http://localhost/svn/", repositories, this.solrCoreProvider);
		
		Set<CmsRepository> set = d.getRepositoriesLoaded();
		
		assertEquals(3, set.size());
		//assertEquals("java.util.TreeMap$KeySet", set.getClass().getName());
		
		Iterator<CmsRepository> it = set.iterator();
		assertEquals("http://localhost/svn/demo1", it.next().toString());
		assertEquals("http://localhost/svn/repo1", it.next().toString());
		assertEquals("http://localhost/svn/repo2", it.next().toString());
	}

}
