-- Schema-only tables with no product API or JPA usage (Agent J6 audit).
-- Runtime LLM caching uses Spring @Cacheable (MetadataLlmResponseCacheService), not this table.

DROP TABLE IF EXISTS response_cache;
DROP TABLE IF EXISTS scheduled_evaluation;
DROP TABLE IF EXISTS prompt_template;
