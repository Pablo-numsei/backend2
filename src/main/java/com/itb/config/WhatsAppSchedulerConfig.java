package com.itb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Outbox processing is disabled unless the Meta integration is fully configured. */
@Configuration
@EnableScheduling
public class WhatsAppSchedulerConfig {}
