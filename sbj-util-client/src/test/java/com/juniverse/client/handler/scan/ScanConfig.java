package com.juniverse.client.handler.scan;

import com.juniverse.client.autoconfigure.EnableRestHttpClients;
import org.springframework.context.annotation.Configuration;

/** Scans its own package (no basePackages given). */
@Configuration
@EnableRestHttpClients
public class ScanConfig {
}
