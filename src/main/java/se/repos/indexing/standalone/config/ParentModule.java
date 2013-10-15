/**
 * Copyright (C) 2004-2012 Repos Mjukvara AB
 */
package se.repos.indexing.standalone.config;

import se.repos.indexing.scheduling.IndexingSchedule;
import se.repos.indexing.scheduling.IndexingScheduleBlockingOnly;

import com.google.inject.AbstractModule;

public class ParentModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(IndexingSchedule.class).to(IndexingScheduleBlockingOnly.class);
	}
	
}
