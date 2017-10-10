/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone;

import javax.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.media.sse.SseFeature;
import org.junit.Test;

public class IndexingDaemonPubSubTest {
	
	@Test
	public void testBuildClient() {		
		//Problems with dependencies of jax.rs.ws causes the SseFeature to crash on init. this test ensures that it is possible for this module to init a client with SseFeature.
		ClientBuilder.newBuilder().register(SseFeature.class).build();
	}

}
