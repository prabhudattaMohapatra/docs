# Knowledge Store Options for AI-Powered Rules Generation

## Overview

The Knowledge Store is the foundational component that stores, organizes, and retrieves regulatory information for AI-powered payroll rules generation. This document outlines the available options, their characteristics, and trade-offs.

---

## Option 1: Vector Database (Semantic Search)

### Description
Store document chunks as vector embeddings, enabling semantic similarity search to retrieve relevant regulatory content.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         VECTOR DATABASE ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                     │
│  │ PDF/HTML    │    │ Text        │    │ Embedding   │                     │
│  │ Documents   │───▶│ Chunker     │───▶│ Model       │                     │
│  └─────────────┘    └─────────────┘    └──────┬──────┘                     │
│                                               │                            │
│                                               ▼                            │
│                                    ┌─────────────────┐                     │
│                                    │  Vector DB      │                     │
│                                    │  ┌───────────┐  │                     │
│                                    │  │ Chunk 1   │  │                     │
│                                    │  │ [0.1, 0.3,│  │                     │
│                                    │  │  ..., 0.8]│  │                     │
│                                    │  ├───────────┤  │                     │
│                                    │  │ Chunk 2   │  │                     │
│                                    │  │ [0.2, 0.1,│  │                     │
│                                    │  │  ..., 0.5]│  │                     │
│                                    │  ├───────────┤  │                     │
│                                    │  │ Chunk N   │  │                     │
│                                    │  │ [...]     │  │                     │
│                                    │  └───────────┘  │                     │
│                                    └────────┬────────┘                     │
│                                             │                              │
│                                             ▼                              │
│                                    ┌─────────────────┐                     │
│                                    │ Semantic Search │                     │
│                                    │ (ANN/HNSW)      │                     │
│                                    └─────────────────┘                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Technology Options

| Product | Type | Pros | Cons |
|---------|------|------|------|
| **Pinecone** | Managed SaaS | Fully managed, fast, scalable | Vendor lock-in, cost at scale |
| **Weaviate** | Open Source / Cloud | Hybrid search, GraphQL API, self-host option | Operational overhead if self-hosted |
| **Qdrant** | Open Source / Cloud | High performance, Rust-based, filtering | Newer, smaller community |
| **Milvus** | Open Source | Enterprise features, GPU support | Complex deployment |
| **pgvector** | PostgreSQL Extension | Use existing Postgres, simple | Limited scale, fewer features |
| **Amazon OpenSearch** | AWS Managed | AWS integration, k-NN plugin | Configuration complexity |
| **Azure AI Search** | Azure Managed | Azure integration, hybrid search | Azure ecosystem dependency |

### Embedding Model Options

| Model | Dimensions | Strengths | Considerations |
|-------|------------|-----------|----------------|
| **OpenAI text-embedding-3-large** | 3072 | High quality, easy API | Cost, data privacy |
| **OpenAI text-embedding-3-small** | 1536 | Good balance cost/quality | Cost, data privacy |
| **Cohere embed-v3** | 1024 | Multilingual, compression | API dependency |
| **Voyage AI** | 1024 | Legal/financial domain tuned | Specialized use case |
| **BGE-large** | 1024 | Open source, self-hostable | Requires GPU infrastructure |
| **E5-large-v2** | 1024 | Open source, good quality | Requires GPU infrastructure |
| **Amazon Titan Embeddings** | 1536 | AWS native | AWS ecosystem only |

### Chunking Strategies

| Strategy | Description | Best For |
|----------|-------------|----------|
| **Fixed Size** | Split by character/token count | Simple documents |
| **Semantic** | Split by meaning/paragraphs | Well-structured docs |
| **Recursive** | Hierarchical splitting | Mixed content |
| **Document-aware** | Respect headers, sections | Regulatory documents |
| **Sliding Window** | Overlapping chunks | Context preservation |

### Pros
- Semantic understanding of queries
- Handles natural language questions well
- Good for exploratory retrieval
- Scales to large document collections

### Cons
- Loss of document structure
- No explicit relationships between concepts
- Chunking can break context
- Embedding quality affects retrieval quality

### Best For
- Large collections of unstructured regulatory documents
- Natural language query interfaces
- When exact structure is less important than meaning

---

## Option 2: Knowledge Graph

### Description
Store regulatory information as entities and relationships in a graph structure, enabling traversal and reasoning.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        KNOWLEDGE GRAPH ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         ENTITY EXTRACTION                            │   │
│  │                                                                      │   │
│  │  Documents ──▶ NER/LLM ──▶ Entities + Relationships                 │   │
│  │                                                                      │   │
│  └──────────────────────────────────┬──────────────────────────────────┘   │
│                                     │                                      │
│                                     ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         KNOWLEDGE GRAPH                              │   │
│  │                                                                      │   │
│  │     ┌──────────┐                           ┌──────────┐             │   │
│  │     │ Country: │      APPLIES_TO           │ Tax Type:│             │   │
│  │     │ France   │◀─────────────────────────▶│ Income   │             │   │
│  │     └────┬─────┘                           └────┬─────┘             │   │
│  │          │                                      │                   │   │
│  │          │ HAS_REGULATION                       │ HAS_BRACKET       │   │
│  │          │                                      │                   │   │
│  │          ▼                                      ▼                   │   │
│  │     ┌──────────┐                           ┌──────────┐             │   │
│  │     │ Social   │      CONTRIBUTES_TO       │ Bracket: │             │   │
│  │     │ Security │◀─────────────────────────▶│ 0-11294€ │             │   │
│  │     │ Fund     │                           │ Rate: 0% │             │   │
│  │     └────┬─────┘                           └──────────┘             │   │
│  │          │                                                          │   │
│  │          │ HAS_RATE                                                 │   │
│  │          ▼                                                          │   │
│  │     ┌──────────┐                                                    │   │
│  │     │ Rate:    │                                                    │   │
│  │     │ 6.90%    │                                                    │   │
│  │     │ Ceiling: │                                                    │   │
│  │     │ PASS     │                                                    │   │
│  │     └──────────┘                                                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         QUERY ENGINE                                 │   │
│  │                                                                      │   │
│  │  Cypher/SPARQL/Gremlin ──▶ Traversal ──▶ Results                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Technology Options

| Product | Type | Query Language | Pros | Cons |
|---------|------|----------------|------|------|
| **Neo4j** | Native Graph | Cypher | Mature, excellent tooling, AuraDB cloud | Cost at scale, Cypher learning curve |
| **Amazon Neptune** | AWS Managed | Gremlin/SPARQL | AWS integration, serverless option | AWS lock-in, complex pricing |
| **Azure Cosmos DB (Gremlin)** | Azure Managed | Gremlin | Multi-model, global distribution | Complexity, cost |
| **TigerGraph** | Enterprise | GSQL | High performance, analytics | Enterprise pricing |
| **ArangoDB** | Multi-model | AQL | Document + Graph, flexible | Jack of all trades |
| **Dgraph** | Native Graph | GraphQL± | GraphQL native, distributed | Smaller ecosystem |

### Ontology Design Considerations

```
REGULATORY ONTOLOGY EXAMPLE:

Country
  └── Jurisdiction (Federal, State, Canton, etc.)
        └── Regulation
              ├── EffectiveDate
              ├── ExpiryDate
              ├── SourceDocument
              └── RuleCategory
                    ├── IncomeTax
                    │     ├── TaxBracket (threshold, rate)
                    │     ├── Deduction (type, amount, conditions)
                    │     └── Credit (type, amount, conditions)
                    ├── SocialSecurity
                    │     ├── EmployeeContribution (fund, rate, ceiling)
                    │     └── EmployerContribution (fund, rate, ceiling)
                    └── EmployerBurden
                          ├── MandatoryContribution
                          └── OptionalContribution
```

### Pros
- Explicit relationships between regulatory concepts
- Supports complex queries and reasoning
- Natural fit for hierarchical regulations
- Excellent for traceability and audit

### Cons
- Requires upfront ontology design
- Entity extraction is complex
- Higher initial setup effort
- May miss nuances in unstructured text

### Best For
- Well-defined regulatory domains
- When relationships between rules matter
- Audit and compliance requirements
- Multi-country rule comparison

---

## Option 3: Hybrid Vector + Graph (GraphRAG)

### Description
Combine vector embeddings with knowledge graph structure for both semantic search and relationship traversal.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          HYBRID GRAPHRAG ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      DOCUMENT PROCESSING                             │   │
│  │                                                                      │   │
│  │  Documents ──┬──▶ Chunking ──▶ Embeddings ──▶ Vector Store          │   │
│  │              │                                                       │   │
│  │              └──▶ Entity Extraction ──▶ Knowledge Graph             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      DUAL STORAGE LAYER                              │   │
│  │                                                                      │   │
│  │  ┌─────────────────────┐        ┌─────────────────────┐             │   │
│  │  │    VECTOR STORE     │        │   KNOWLEDGE GRAPH   │             │   │
│  │  │                     │        │                     │             │   │
│  │  │  • Document chunks  │◀──────▶│  • Entities         │             │   │
│  │  │  • Embeddings       │  LINK  │  • Relationships    │             │   │
│  │  │  • Metadata         │        │  • Properties       │             │   │
│  │  │                     │        │                     │             │   │
│  │  └─────────────────────┘        └─────────────────────┘             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      HYBRID RETRIEVAL                                │   │
│  │                                                                      │   │
│  │  Query ──┬──▶ Semantic Search ──▶ Relevant Chunks                   │   │
│  │          │                              │                            │   │
│  │          │                              ▼                            │   │
│  │          │                       ┌─────────────┐                     │   │
│  │          └──▶ Graph Traversal ──▶│   FUSION    │──▶ Rich Context    │   │
│  │                     │            └─────────────┘                     │   │
│  │                     ▼                                                │   │
│  │              Related Entities                                        │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Technology Combinations

| Vector Store | Graph DB | Integration Approach |
|--------------|----------|---------------------|
| Pinecone | Neo4j | Application-level join |
| pgvector | Apache AGE (Postgres) | Single database |
| Weaviate | Neo4j | Weaviate references |
| Qdrant | Neo4j | Application-level join |
| Neo4j Vector Index | Neo4j | Native integration |

### Implementation Options

**Option A: Neo4j with Vector Index (Native)**
```
Neo4j 5.x+ supports native vector indexes, combining both in one database.
- Store entities as nodes
- Store embeddings as node properties
- Query with both Cypher and vector similarity
```

**Option B: Weaviate with Cross-References**
```
Weaviate supports references between objects.
- Store chunks with embeddings
- Create cross-references to related chunks
- Build implicit graph through references
```

**Option C: Separate Stores with Application Join**
```
Keep specialized stores, join at application level.
- Vector store for semantic retrieval
- Graph DB for relationship traversal
- Application orchestrates both queries
```

### Pros
- Best of both worlds
- Semantic search + relationship reasoning
- Flexible query patterns
- Rich context for LLM

### Cons
- Highest complexity
- Data synchronization challenges
- Multiple systems to maintain
- Query orchestration overhead

### Best For
- Complex regulatory domains
- When both semantic similarity and relationships matter
- Production systems requiring high accuracy

---

## Option 4: Structured Document Store

### Description
Store extracted regulatory data as structured JSON/documents in a document database, with full-text search.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    STRUCTURED DOCUMENT STORE ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      EXTRACTION PIPELINE                             │   │
│  │                                                                      │   │
│  │  Raw Docs ──▶ LLM Extraction ──▶ Structured JSON ──▶ Validation     │   │
│  │                                                                      │   │
│  └─────────────────────────────────┬───────────────────────────────────┘   │
│                                    │                                       │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      DOCUMENT DATABASE                               │   │
│  │                                                                      │   │
│  │  {                                                                   │   │
│  │    "country": "France",                                              │   │
│  │    "year": 2026,                                                     │   │
│  │    "income_tax": {                                                   │   │
│  │      "brackets": [                                                   │   │
│  │        {"min": 0, "max": 11294, "rate": 0},                         │   │
│  │        {"min": 11295, "max": 28797, "rate": 11},                    │   │
│  │        {"min": 28798, "max": 82341, "rate": 30},                    │   │
│  │        ...                                                           │   │
│  │      ]                                                               │   │
│  │    },                                                                │   │
│  │    "social_security": {...},                                         │   │
│  │    "employer_contributions": {...}                                   │   │
│  │  }                                                                   │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      QUERY CAPABILITIES                              │   │
│  │                                                                      │   │
│  │  • JSON path queries                                                 │   │
│  │  • Full-text search                                                  │   │
│  │  • Aggregations                                                      │   │
│  │  • Versioning                                                        │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Technology Options

| Product | Type | Pros | Cons |
|---------|------|------|------|
| **MongoDB** | Document DB | Flexible schema, rich queries, Atlas Search | Schema discipline needed |
| **Amazon DocumentDB** | AWS Managed | MongoDB compatible, AWS integration | Feature lag vs MongoDB |
| **Azure Cosmos DB** | Multi-model | Global distribution, multiple APIs | Cost, complexity |
| **Elasticsearch** | Search Engine | Excellent full-text search, aggregations | Operational overhead |
| **PostgreSQL (JSONB)** | Relational + JSON | Familiar, ACID, use existing infra | Less specialized |
| **CouchDB** | Document DB | Simple, replication | Limited query capabilities |

### Schema Design

```json
{
  "$schema": "regulatory_data_v1",
  "metadata": {
    "country": "string",
    "jurisdiction": "string",
    "effective_date": "date",
    "expiry_date": "date",
    "source_documents": ["array"],
    "extraction_date": "datetime",
    "version": "string"
  },
  "income_tax": {
    "filing_status_types": ["array"],
    "brackets": [{
      "filing_status": "string",
      "min_income": "number",
      "max_income": "number",
      "rate_percent": "number",
      "fixed_amount": "number"
    }],
    "standard_deductions": [{
      "type": "string",
      "amount": "number",
      "conditions": ["array"]
    }],
    "credits": [{
      "name": "string",
      "type": "refundable|non-refundable",
      "amount": "number",
      "phase_out": {}
    }]
  },
  "social_security": {
    "employee_contributions": [{
      "fund_name": "string",
      "fund_code": "string",
      "rate_percent": "number",
      "ceiling_type": "string",
      "ceiling_amount": "number"
    }],
    "employer_contributions": [{
      "fund_name": "string",
      "fund_code": "string",
      "rate_percent": "number",
      "ceiling_type": "string",
      "ceiling_amount": "number"
    }]
  },
  "employer_burden": {
    "mandatory": [{
      "name": "string",
      "rate_percent": "number",
      "base": "string"
    }],
    "optional": []
  }
}
```

### Pros
- Highly structured and queryable
- Schema validation possible
- Easy to transform to rule format
- Version control friendly
- Human readable

### Cons
- Requires upfront schema design
- LLM extraction must match schema
- Less flexible for unstructured content
- Schema evolution challenges

### Best For
- When regulatory structure is well-understood
- Template-based rule generation
- When auditability is critical
- Multi-format output requirements

---

## Option 5: File-Based with Git Version Control

### Description
Store regulatory data as files (JSON/YAML/Markdown) in a Git repository with version control.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FILE-BASED GIT ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      REPOSITORY STRUCTURE                            │   │
│  │                                                                      │   │
│  │  regulations/                                                        │   │
│  │  ├── france/                                                         │   │
│  │  │   ├── 2026/                                                       │   │
│  │  │   │   ├── income_tax.json                                        │   │
│  │  │   │   ├── social_security.json                                   │   │
│  │  │   │   ├── employer_contributions.json                            │   │
│  │  │   │   └── sources/                                               │   │
│  │  │   │       ├── tax_authority_2026.pdf                             │   │
│  │  │   │       └── urssaf_rates_2026.pdf                              │   │
│  │  │   └── 2025/                                                       │   │
│  │  │       └── ...                                                     │   │
│  │  ├── germany/                                                        │   │
│  │  │   └── ...                                                         │   │
│  │  └── schemas/                                                        │   │
│  │      ├── income_tax.schema.json                                     │   │
│  │      └── social_security.schema.json                                │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      GIT-BASED FEATURES                              │   │
│  │                                                                      │   │
│  │  • Full history of all changes                                       │   │
│  │  • Branch for draft regulations                                      │   │
│  │  • Pull requests for review                                          │   │
│  │  • CI/CD for validation                                              │   │
│  │  • Blame for audit trail                                             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      ACCESS PATTERNS                                 │   │
│  │                                                                      │   │
│  │  Read: Clone repo / Fetch files / API (GitHub/GitLab)               │   │
│  │  Search: grep / ripgrep / GitHub Search                              │   │
│  │  Index: Load into memory / SQLite / Search index                    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Pros
- Simple, no database needed
- Excellent version control
- Human-editable
- Built-in review process (PRs)
- Easy backup and replication
- Works with existing repos (e.g., `payroll-engine-regulations-*`)

### Cons
- Limited query capabilities
- No built-in semantic search
- Scale limitations for large datasets
- Requires indexing for efficient retrieval

### Best For
- Small to medium regulation sets
- When human review is essential
- Integration with existing Git workflows
- When simplicity is valued

---

## Comparison Matrix

| Criteria | Vector DB | Knowledge Graph | Hybrid | Document Store | File-Based |
|----------|-----------|-----------------|--------|----------------|------------|
| **Semantic Search** | ★★★★★ | ★★☆☆☆ | ★★★★★ | ★★★☆☆ | ★☆☆☆☆ |
| **Relationship Queries** | ★☆☆☆☆ | ★★★★★ | ★★★★☆ | ★★☆☆☆ | ★☆☆☆☆ |
| **Structured Queries** | ★★☆☆☆ | ★★★★☆ | ★★★☆☆ | ★★★★★ | ★★☆☆☆ |
| **Setup Complexity** | ★★☆☆☆ | ★★★★☆ | ★★★★★ | ★★★☆☆ | ★☆☆☆☆ |
| **Operational Overhead** | ★★★☆☆ | ★★★★☆ | ★★★★★ | ★★★☆☆ | ★☆☆☆☆ |
| **Auditability** | ★★☆☆☆ | ★★★★☆ | ★★★★☆ | ★★★★☆ | ★★★★★ |
| **Version Control** | ★★☆☆☆ | ★★☆☆☆ | ★★☆☆☆ | ★★★☆☆ | ★★★★★ |
| **Human Readability** | ★☆☆☆☆ | ★★★☆☆ | ★★☆☆☆ | ★★★★☆ | ★★★★★ |
| **Scale** | ★★★★★ | ★★★★☆ | ★★★★☆ | ★★★★★ | ★★☆☆☆ |
| **Cost** | ★★★☆☆ | ★★☆☆☆ | ★★☆☆☆ | ★★★☆☆ | ★★★★★ |

---

## Recommendation

### For Initial Implementation
**Option 4 (Structured Document Store) + Option 5 (File-Based Git)**

- Store extracted regulatory data as JSON files in Git repos
- Index into Elasticsearch/MongoDB for query capabilities
- Maintains human readability and version control
- Aligns with existing `payroll-engine-regulations-*` repos

### For Production Scale
**Option 3 (Hybrid Vector + Graph)**

- Add vector embeddings for semantic retrieval
- Build knowledge graph for relationship queries
- Use Neo4j with vector index for unified solution

### Migration Path
```
File-Based (MVP) 
    ──▶ Document Store (Indexed) 
        ──▶ Add Vector Search 
            ──▶ Full Hybrid GraphRAG
```

---

*Document created: January 2026*

