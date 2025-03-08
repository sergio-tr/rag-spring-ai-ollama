-- docker run --name postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -v ~/postgres-volume/:/var/lib/postgresql/data -d pgvector/pgvector:pg17
-- docker exec -it postgres psql -U postgres -d postgres
-- CREATE DATABASE vectordb
-- \c vectordb
-- Drop tables if they exist
DROP TABLE IF EXISTS vector_store CASCADE;
DROP TABLE IF EXISTS documents CASCADE;

-- Create required extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create the documents table
CREATE TABLE documents (
   id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
   document_name TEXT UNIQUE NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
   metadata JSONB
);

-- Create the vector_store table with reference to documents
CREATE TABLE vector_store (
  id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  content TEXT,
  metadata JSONB,
  embedding vector(1024),
  chunk_index INTEGER,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_documents_document_name ON documents(document_name);
CREATE INDEX idx_vector_store_embedding ON vector_store USING HNSW (embedding vector_cosine_ops);
