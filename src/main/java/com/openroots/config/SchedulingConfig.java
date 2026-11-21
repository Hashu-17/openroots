package com.openroots.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables scheduled jobs, such as disabling broker logins of expired device credentials. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
