package com.datacrowd.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String WORKER_STATS       = "worker-stats";
    public static final String AVAILABLE_PROJECTS = "available-projects";
    public static final String MY_PROJECTS        = "my-projects";
    public static final String DATA_TYPE_GUIDES   = "data-type-guides";
    public static final String PROJECT_PUBLIC     = "project-public";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.registerCustomCache(WORKER_STATS,
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .maximumSize(10_000)
                        .build());

        manager.registerCustomCache(AVAILABLE_PROJECTS,
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(1)
                        .build());

        manager.registerCustomCache(MY_PROJECTS,
                Caffeine.newBuilder()
                        .expireAfterWrite(20, TimeUnit.SECONDS)
                        .maximumSize(5_000)
                        .build());

        manager.registerCustomCache(DATA_TYPE_GUIDES,
                Caffeine.newBuilder()
                        .maximumSize(1)
                        .build());

        manager.registerCustomCache(PROJECT_PUBLIC,
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(1_000)
                        .build());

        return manager;
    }
}