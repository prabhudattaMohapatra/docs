# AI-Powered Payroll Rules Generation - System Architecture

## Overview

This document outlines system architecture options for generating gross-to-net calculation rules using AI for salaried employees, including employer burden and tax compliance rules.

### Requirements

**Input:**
- Authoritative regulatory sources for income and tax rules (tax authority publications, labor laws, social security documents, government gazettes)
- A prompt specifying the format and specifications

**Output:**
- Exhaustive rules for gross-to-net calculation
- Employer burden rules
- Tax compliance rules
- Output in specified file format (JSON/YAML/DSL/Code)

---

## Architecture Options

### Architecture 1: Document-Centric RAG Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DOCUMENT INGESTION LAYER                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Tax Authority│  │ Labor Laws   │  │ Social       │  │ Government   │    │
│  │ Publications │  │ Documents    │  │ Security Docs│  │ Gazettes     │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                 │                 │                 │            │
│         └────────────┬────┴─────────────────┴────┬────────────┘            │
│                      ▼                           ▼                         │
│              ┌───────────────┐          ┌───────────────┐                  │
│              │ PDF/HTML      │          │ Document      │                  │
│              │ Parser        │          │ Chunker       │                  │
│              └───────┬───────┘          └───────┬───────┘                  │
│                      │                          │                          │
│                      └──────────┬───────────────┘                          │
│                                 ▼                                          │
│                      ┌───────────────────┐                                 │
│                      │ Vector Embedding  │                                 │
│                      │ (OpenAI/Cohere)   │                                 │
│                      └─────────┬─────────┘                                 │
│                                ▼                                           │
│                      ┌───────────────────┐                                 │
│                      │ Vector DB         │                                 │
│                      │ (Pinecone/Weaviate│                                 │
│                      │  /pgvector)       │                                 │
│                      └─────────┬─────────┘                                 │
└────────────────────────────────┼────────────────────────────────────────────┘
                                 │
┌────────────────────────────────┼────────────────────────────────────────────┐
│                    RULES GENERATION LAYER                                   │
├────────────────────────────────┼────────────────────────────────────────────┤
│                                ▼                                            │
│  ┌──────────────┐    ┌─────────────────┐    ┌──────────────────┐           │
│  │ User Prompt  │───▶│ RAG Retriever   │───▶│ Context Builder  │           │
│  │ (Format Spec)│    │                 │    │                  │           │
│  └──────────────┘    └─────────────────┘    └────────┬─────────┘           │
│                                                      │                      │
│                                                      ▼                      │
│                                             ┌────────────────┐              │
│                                             │ LLM (GPT-4/    │              │
│                                             │ Claude)        │              │
│                                             └────────┬───────┘              │
│                                                      │                      │
│                                                      ▼                      │
│                                             ┌────────────────┐              │
│                                             │ Rules Output   │              │
│                                             │ (JSON/YAML/DSL)│              │
│                                             └────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Simple and fast to implement
- Leverages existing RAG patterns
- Lower infrastructure complexity

**Cons:**
- May miss complex rule interdependencies
- Single-shot generation can have gaps
- Limited traceability

---

### Architecture 2: Multi-Agent Orchestration Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ORCHESTRATOR                                   │
│                          (LangGraph / AutoGen)                              │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│   RESEARCH    │      │   EXTRACTION  │      │   GENERATION  │
│    AGENT      │─────▶│    AGENT      │─────▶│    AGENT      │
├───────────────┤      ├───────────────┤      ├───────────────┤
│ • Find sources│      │ • Extract tax │      │ • Generate    │
│ • Verify auth │      │   brackets    │      │   rule DSL    │
│ • Download    │      │ • Extract     │      │ • Format to   │
│   documents   │      │   thresholds  │      │   spec        │
│ • Categorize  │      │ • Identify    │      │ • Add metadata│
│               │      │   formulas    │      │               │
└───────────────┘      └───────────────┘      └───────┬───────┘
                                                      │
                                                      ▼
                                              ┌───────────────┐
                                              │  VALIDATION   │
                                              │    AGENT      │
                                              ├───────────────┤
                                              │ • Check       │
                                              │   completeness│
                                              │ • Verify math │
                                              │ • Test cases  │
                                              │ • Flag gaps   │
                                              └───────┬───────┘
                                                      │
                                                      ▼
                                              ┌───────────────┐
                                              │    HUMAN      │
                                              │   REVIEW      │
                                              │   INTERFACE   │
                                              └───────────────┘
```

**Pros:**
- Specialized agents for each task
- Iterative refinement capabilities
- Better for complex regulations
- Built-in validation step

**Cons:**
- Higher complexity
- More expensive (multiple LLM calls)
- Orchestration overhead

---

### Architecture 3: Structured Knowledge Extraction Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DOCUMENT PROCESSING                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                     │
│  │ Raw Docs    │───▶│ Document    │───▶│ Section     │                     │
│  │ (PDF/HTML)  │    │ Intelligence│    │ Classifier  │                     │
│  └─────────────┘    │ (Azure DI/  │    │             │                     │
│                     │ AWS Textract│    └──────┬──────┘                     │
│                     └─────────────┘           │                            │
│                                               │                            │
│    ┌──────────────────────────────────────────┴────────────────────────┐   │
│    │                                                                   │   │
│    ▼                    ▼                    ▼                    ▼    │   │
│ ┌────────┐         ┌────────┐          ┌────────┐          ┌────────┐ │   │
│ │Income  │         │Social  │          │Employer│          │Tax     │ │   │
│ │Tax     │         │Security│          │Contrib │          │Credits │ │   │
│ │Section │         │Section │          │Section │          │Section │ │   │
│ └───┬────┘         └───┬────┘          └───┬────┘          └───┬────┘ │   │
│     │                  │                   │                   │      │   │
└─────┼──────────────────┼───────────────────┼───────────────────┼──────┘   │
      │                  │                   │                   │          │
      └──────────────────┴─────────┬─────────┴───────────────────┘          │
                                   │                                        │
┌──────────────────────────────────┼────────────────────────────────────────┐
│              ENTITY EXTRACTION & KNOWLEDGE GRAPH                          │
├──────────────────────────────────┼────────────────────────────────────────┤
│                                  ▼                                        │
│                    ┌──────────────────────┐                               │
│                    │  Named Entity        │                               │
│                    │  Recognition (NER)   │                               │
│                    │  + LLM Extraction    │                               │
│                    └──────────┬───────────┘                               │
│                               │                                           │
│         ┌─────────────────────┼─────────────────────┐                     │
│         ▼                     ▼                     ▼                     │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐               │
│  │ Tax Rates & │      │ Thresholds &│      │ Formulas &  │               │
│  │ Brackets    │      │ Limits      │      │ Calculations│               │
│  └──────┬──────┘      └──────┬──────┘      └──────┬──────┘               │
│         │                    │                    │                       │
│         └────────────────────┼────────────────────┘                       │
│                              ▼                                            │
│                    ┌──────────────────┐                                   │
│                    │  Knowledge Graph │                                   │
│                    │  (Neo4j/Neptune) │                                   │
│                    └────────┬─────────┘                                   │
└─────────────────────────────┼─────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼─────────────────────────────────────────────┐
│              RULE GENERATION ENGINE                                       │
├─────────────────────────────┼─────────────────────────────────────────────┤
│                             ▼                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                  │
│  │ Rule Template│   │ Graph Query  │   │ LLM Rule     │                  │
│  │ Library      │──▶│ Engine       │──▶│ Composer     │                  │
│  └──────────────┘   └──────────────┘   └──────┬───────┘                  │
│                                               │                          │
│                                               ▼                          │
│                                       ┌──────────────┐                   │
│                                       │ Output       │                   │
│                                       │ Formatter    │                   │
│                                       │ (JSON/YAML/  │                   │
│                                       │  DSL/Code)   │                   │
│                                       └──────────────┘                   │
└───────────────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Best for regulatory compliance requirements
- Traceable and auditable
- Handles regulatory updates well
- Structured data enables validation

**Cons:**
- Highest implementation effort
- Requires domain modeling upfront
- Knowledge graph maintenance overhead

---

### Architecture 4: Hybrid Two-Phase Generation (Recommended)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PHASE 1: STRUCTURED EXTRACTION                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Source Documents                                  │   │
│  └─────────────────────────────────┬───────────────────────────────────┘   │
│                                    ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │               LLM + Structured Output (JSON Mode)                    │   │
│  │  ┌───────────────────────────────────────────────────────────────┐  │   │
│  │  │ {                                                              │  │   │
│  │  │   "country": "France",                                         │  │   │
│  │  │   "effective_date": "2026-01-01",                              │  │   │
│  │  │   "income_tax": {                                              │  │   │
│  │  │     "brackets": [...],                                         │  │   │
│  │  │     "deductions": [...]                                        │  │   │
│  │  │   },                                                           │  │   │
│  │  │   "social_security": {...},                                    │  │   │
│  │  │   "employer_contributions": {...}                              │  │   │
│  │  │ }                                                              │  │   │
│  │  └───────────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────┬───────────────────────────────────┘   │
│                                    │                                       │
└────────────────────────────────────┼───────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                   INTERMEDIATE STRUCTURED DATA STORE                        │
│                         (Versioned, Auditable)                              │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      PHASE 2: RULE GENERATION                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐                                                          │
│  │ User Prompt  │  "Generate rules in PayrollEngine DSL format with..."    │
│  │ (Format Spec)│                                                          │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │              RULE GENERATION ENGINE                                  │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐      │   │
│  │  │ Template Engine │  │ LLM Composer    │  │ Schema Validator│      │   │
│  │  │ (for standard   │  │ (for complex    │  │                 │      │   │
│  │  │  patterns)      │  │  rules)         │  │                 │      │   │
│  │  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘      │   │
│  │           │                    │                    │               │   │
│  │           └────────────────────┴────────────────────┘               │   │
│  │                                │                                    │   │
│  └────────────────────────────────┼────────────────────────────────────┘   │
│                                   ▼                                        │
│                         ┌─────────────────┐                                │
│                         │  OUTPUT FILES   │                                │
│                         │  (.json/.yaml/  │                                │
│                         │   .rules/.cs)   │                                │
│                         └─────────────────┘                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Decouples extraction from generation
- Intermediate data is reusable across formats
- Format-agnostic intermediate representation
- Easier to audit and validate
- Supports incremental updates

**Cons:**
- Two-phase adds latency
- Need to maintain intermediate schema

---

## Recommended Implementation

### Technology Stack

| Component | Suggested Technology |
|-----------|---------------------|
| **Document Ingestion** | Azure Document Intelligence / AWS Textract |
| **Vector Store** | pgvector (if PostgreSQL) or Pinecone |
| **LLM** | Claude/GPT-4 with structured output mode |
| **Orchestration** | LangGraph or AWS Step Functions |
| **Intermediate Store** | Versioned JSON in S3 or database |
| **Rule Generation** | Template engine + LLM for complex cases |
| **Validation** | JSON Schema validation + test case runner |
| **Output Format** | PayrollEngine DSL format |

### Data Flow

```
[Regulatory Sources] 
       │
       ▼
[Document Intelligence / OCR]
       │
       ▼
[Chunking & Embedding]
       │
       ▼
[Vector Store]
       │
       ▼
[RAG + LLM Extraction] ──────────────────┐
       │                                  │
       ▼                                  │
[Structured Intermediate JSON] ◄─────────┘
       │                         (Human Review Point)
       ▼
[Rule Generation Engine]
       │
       ├──▶ [Template-based Rules] (standard patterns)
       │
       └──▶ [LLM-composed Rules] (complex logic)
              │
              ▼
       [Schema Validation]
              │
              ▼
       [Output Files (.json/.yaml/.cs)]
              │
              ▼
       [Integration Testing with Payroll Engine]
```

---

## Key Design Considerations

### 1. Source Document Management
- Maintain a registry of authoritative sources per country
- Track document versions and effective dates
- Store original documents for audit trail

### 2. Intermediate Data Schema
Define a country-agnostic schema for extracted regulatory data:

```json
{
  "country": "string",
  "jurisdiction": "string",
  "effective_date": "date",
  "expiry_date": "date",
  "source_documents": ["array of document references"],
  "income_tax": {
    "brackets": [],
    "deductions": [],
    "credits": [],
    "special_regimes": []
  },
  "social_security": {
    "employee_contributions": [],
    "employer_contributions": [],
    "ceilings": []
  },
  "employer_burden": {
    "mandatory_contributions": [],
    "optional_contributions": []
  }
}
```

### 3. Validation Strategy
- **Schema Validation:** Ensure output conforms to expected format
- **Mathematical Validation:** Verify calculations produce expected results
- **Test Case Validation:** Run against known payroll scenarios
- **Completeness Check:** Ensure all required rule categories are covered

### 4. Human-in-the-Loop Points
- After document extraction (verify correct parsing)
- After intermediate data generation (verify accuracy)
- After rule generation (final review before deployment)

---

## Questions to Resolve Before Implementation

1. **What authoritative sources are being targeted?**
   - Tax authority websites
   - Official gazettes
   - Labor ministry publications
   - Social security agency documents

2. **What is the target output format?**
   - PayrollEngine DSL
   - JSON/YAML configuration
   - C# code generation

3. **What's the update cadence?**
   - Regulatory changes require re-running the pipeline
   - Annual updates vs. ad-hoc changes

4. **What level of human review is acceptable?**
   - Fully automated
   - Human-in-the-loop for validation
   - Human approval required before deployment

5. **Do you need audit trails for compliance?**
   - Traceability from source document to generated rule
   - Version history of all rules
   - Change justification documentation

---

## Related Resources

- `payroll-engine-ai-rules-pipeline` - Existing AI rules pipeline implementation
- `payroll-engine-regulations-*` - Country-specific regulation repositories
- `payroll_dsl_evaluation` - DSL evaluation tools
- `FR_business_term_mapping.json` - France business term mappings

---

*Document created: January 2026*

