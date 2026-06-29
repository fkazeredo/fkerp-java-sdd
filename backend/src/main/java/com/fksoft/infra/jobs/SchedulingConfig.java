package com.fksoft.infra.jobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables Spring's scheduling so technical jobs (e.g. the booking PENDING-timeout sweep) run. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
