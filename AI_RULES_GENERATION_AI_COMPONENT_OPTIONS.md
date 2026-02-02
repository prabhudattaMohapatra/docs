# AI Component Options for Payroll Rules Generation

## Overview

This document outlines the AI/LLM component options for consuming knowledge from the knowledge store and generating payroll rules. The AI component is responsible for:

1. **Extraction**: Parsing regulatory documents into structured data
2. **Retrieval**: Finding relevant information from the knowledge store
3. **Generation**: Producing rules in the target format
4. **Validation**: Verifying completeness and correctness

---

## Component 1: LLM Selection

### Option A: Commercial API-Based LLMs

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API-BASED LLM ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                     │
│  │ Application │───▶│ API Gateway │───▶│ LLM Provider│                     │
│  │             │    │             │    │ (OpenAI/    │                     │
│  │             │◀───│             │◀───│ Anthropic)  │                     │
│  └─────────────┘    └─────────────┘    └─────────────┘                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Model | Provider | Context Window | Strengths | Considerations |
|-------|----------|----------------|-----------|----------------|
| **GPT-4o** | OpenAI | 128K | Best overall, multimodal, fast | Cost, data privacy |
| **GPT-4 Turbo** | OpenAI | 128K | Strong reasoning, JSON mode | Cost |
| **Claude 3.5 Sonnet** | Anthropic | 200K | Large context, excellent analysis | Anthropic policies |
| **Claude 3 Opus** | Anthropic | 200K | Best for complex reasoning | Higher cost |
| **Gemini 1.5 Pro** | Google | 1M+ | Massive context window | API stability |
| **Gemini 1.5 Flash** | Google | 1M+ | Fast, cost-effective | Less capable |
| **Command R+** | Cohere | 128K | RAG optimized, grounded generation | Smaller ecosystem |

#### Pricing Comparison (as of 2026, approximate)

| Model | Input (per 1M tokens) | Output (per 1M tokens) |
|-------|----------------------|------------------------|
| GPT-4o | $2.50 | $10.00 |
| GPT-4 Turbo | $10.00 | $30.00 |
| Claude 3.5 Sonnet | $3.00 | $15.00 |
| Claude 3 Opus | $15.00 | $75.00 |
| Gemini 1.5 Pro | $3.50 | $10.50 |
| Gemini 1.5 Flash | $0.075 | $0.30 |

### Option B: Cloud-Hosted LLMs (AWS/Azure/GCP)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      CLOUD-HOSTED LLM ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         YOUR CLOUD VPC                               │   │
│  │                                                                      │   │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐              │   │
│  │  │ Application │───▶│ Cloud LLM   │───▶│ Model       │              │   │
│  │  │             │    │ Service     │    │ Endpoint    │              │   │
│  │  │             │◀───│ (Bedrock/   │◀───│             │              │   │
│  │  │             │    │  Azure/     │    │             │              │   │
│  │  │             │    │  Vertex)    │    │             │              │   │
│  │  └─────────────┘    └─────────────┘    └─────────────┘              │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Service | Provider | Available Models | Pros | Cons |
|---------|----------|------------------|------|------|
| **Amazon Bedrock** | AWS | Claude, Llama, Titan, Mistral | AWS integration, private endpoints | Model availability lag |
| **Azure OpenAI** | Microsoft | GPT-4, GPT-4o, embeddings | Enterprise compliance, Azure integration | Regional availability |
| **Vertex AI** | Google | Gemini, PaLM 2 | Google ecosystem, Gemini access | GCP dependency |

### Option C: Self-Hosted Open Source LLMs

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      SELF-HOSTED LLM ARCHITECTURE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         YOUR INFRASTRUCTURE                          │   │
│  │                                                                      │   │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐              │   │
│  │  │ Application │───▶│ Inference   │───▶│ GPU Cluster │              │   │
│  │  │             │    │ Server      │    │ (A100/H100) │              │   │
│  │  │             │◀───│ (vLLM/TGI)  │◀───│             │              │   │
│  │  └─────────────┘    └─────────────┘    └─────────────┘              │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Model | Parameters | Context | Strengths | Requirements |
|-------|------------|---------|-----------|--------------|
| **Llama 3.1 405B** | 405B | 128K | Near GPT-4 quality | 8x A100 80GB minimum |
| **Llama 3.1 70B** | 70B | 128K | Good balance | 2x A100 80GB |
| **Llama 3.1 8B** | 8B | 128K | Fast, efficient | 1x A100 or consumer GPU |
| **Mixtral 8x22B** | 141B MoE | 64K | Efficient MoE architecture | 4x A100 80GB |
| **Mistral Large** | ~123B | 128K | Strong reasoning | 4x A100 80GB |
| **Qwen2 72B** | 72B | 128K | Multilingual, coding | 2x A100 80GB |
| **DeepSeek V2** | 236B MoE | 128K | Cost-efficient MoE | 4x A100 80GB |

#### Inference Frameworks

| Framework | Pros | Cons |
|-----------|------|------|
| **vLLM** | Fast, PagedAttention, production-ready | Memory intensive |
| **Text Generation Inference (TGI)** | HuggingFace native, Flash Attention | Less flexible |
| **Ollama** | Simple local deployment | Limited scale |
| **LMDeploy** | Optimized for Chinese models | Smaller community |
| **SGLang** | Structured generation optimized | Newer |

### LLM Selection Matrix

| Criteria | API (GPT-4o/Claude) | Cloud (Bedrock/Azure) | Self-Hosted |
|----------|---------------------|----------------------|-------------|
| **Quality** | ★★★★★ | ★★★★☆ | ★★★☆☆ |
| **Data Privacy** | ★★☆☆☆ | ★★★★☆ | ★★★★★ |
| **Cost (Low Volume)** | ★★★★☆ | ★★★★☆ | ★☆☆☆☆ |
| **Cost (High Volume)** | ★★☆☆☆ | ★★★☆☆ | ★★★★★ |
| **Setup Complexity** | ★★★★★ | ★★★★☆ | ★★☆☆☆ |
| **Operational Burden** | ★★★★★ | ★★★★☆ | ★★☆☆☆ |
| **Latency Control** | ★★☆☆☆ | ★★★☆☆ | ★★★★★ |
| **Customization** | ★☆☆☆☆ | ★★☆☆☆ | ★★★★★ |

---

## Component 2: Retrieval Strategy

### Option A: Basic RAG (Retrieval Augmented Generation)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BASIC RAG PIPELINE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │  User    │───▶│ Embed    │───▶│ Vector   │───▶│ Top-K    │             │
│  │  Query   │    │ Query    │    │ Search   │    │ Results  │             │
│  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘             │
│                                                       │                    │
│                                                       ▼                    │
│                                               ┌──────────────┐             │
│                                               │ Prompt +     │             │
│                                               │ Context      │────▶ LLM   │
│                                               └──────────────┘             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Pros**: Simple, well-understood, quick to implement  
**Cons**: Limited context, no re-ranking, single retrieval pass

### Option B: Advanced RAG with Re-Ranking

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ADVANCED RAG WITH RE-RANKING                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │  User    │───▶│ Query    │───▶│ Hybrid   │───▶│ Initial  │             │
│  │  Query   │    │ Expansion│    │ Search   │    │ Results  │             │
│  └──────────┘    └──────────┘    │ (Vector +│    │ (Top 50) │             │
│                                  │  BM25)   │    └────┬─────┘             │
│                                  └──────────┘         │                    │
│                                                       ▼                    │
│                                               ┌──────────────┐             │
│                                               │ Cross-Encoder│             │
│                                               │ Re-Ranker    │             │
│                                               └──────┬───────┘             │
│                                                      │                     │
│                                                      ▼                     │
│                                               ┌──────────────┐             │
│                                               │ Top-K        │             │
│                                               │ Re-Ranked    │────▶ LLM   │
│                                               └──────────────┘             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Re-Ranking Models

| Model | Type | Pros | Cons |
|-------|------|------|------|
| **Cohere Rerank** | API | High quality, easy | Cost |
| **bge-reranker-v2-m3** | Open Source | Multilingual, self-host | Compute needed |
| **ms-marco-MiniLM** | Open Source | Fast, lightweight | English focused |
| **Jina Reranker** | API/Open | Good performance | Newer |

### Option C: Agentic RAG (Self-Correcting)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AGENTIC RAG PIPELINE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         AGENT LOOP                                    │  │
│  │                                                                       │  │
│  │  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐       │  │
│  │  │  Query   │───▶│ Plan     │───▶│ Retrieve │───▶│ Evaluate │       │  │
│  │  │          │    │          │    │          │    │          │       │  │
│  │  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘       │  │
│  │                       ▲                               │              │  │
│  │                       │                               │              │  │
│  │                       │         ┌─────────────────────┘              │  │
│  │                       │         │                                    │  │
│  │                       │         ▼                                    │  │
│  │                  ┌────┴────┐  ┌──────────┐                          │  │
│  │                  │ Refine  │◀─│ Sufficient│                          │  │
│  │                  │ Query   │  │ Context? │                          │  │
│  │                  └─────────┘  └────┬─────┘                          │  │
│  │                                    │ Yes                             │  │
│  │                                    ▼                                 │  │
│  │                              ┌──────────┐                           │  │
│  │                              │ Generate │                           │  │
│  │                              │ Response │                           │  │
│  │                              └──────────┘                           │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Pros**: Self-correcting, handles complex queries, multi-step retrieval  
**Cons**: Higher latency, more LLM calls, complex to debug

### Option D: Graph-Enhanced RAG (GraphRAG)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         GRAPH-ENHANCED RAG                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐                                                              │
│  │  Query   │                                                              │
│  └────┬─────┘                                                              │
│       │                                                                    │
│       ├───────────────────────────────┐                                    │
│       │                               │                                    │
│       ▼                               ▼                                    │
│  ┌──────────┐                    ┌──────────┐                              │
│  │ Vector   │                    │ Graph    │                              │
│  │ Search   │                    │ Traversal│                              │
│  └────┬─────┘                    └────┬─────┘                              │
│       │                               │                                    │
│       │  Relevant Chunks              │  Related Entities                  │
│       │                               │                                    │
│       └───────────────┬───────────────┘                                    │
│                       │                                                    │
│                       ▼                                                    │
│                ┌─────────────┐                                             │
│                │   FUSION    │                                             │
│                │   ENGINE    │                                             │
│                └──────┬──────┘                                             │
│                       │                                                    │
│                       ▼                                                    │
│                ┌─────────────┐                                             │
│                │  Rich       │                                             │
│                │  Context    │────▶ LLM                                    │
│                └─────────────┘                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Pros**: Combines semantic + structural understanding, excellent for regulations  
**Cons**: Requires knowledge graph, highest complexity

---

## Component 3: Generation Patterns

### Pattern A: Single-Shot Generation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SINGLE-SHOT GENERATION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                         PROMPT                                      │    │
│  │                                                                     │    │
│  │  System: You are a payroll rules expert...                         │    │
│  │                                                                     │    │
│  │  Context: [Retrieved regulatory documents]                         │    │
│  │                                                                     │    │
│  │  Task: Generate gross-to-net rules for France in JSON format...    │    │
│  │                                                                     │    │
│  │  Output Schema: { "rules": [...] }                                 │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                    │                                       │
│                                    ▼                                       │
│                              ┌──────────┐                                  │
│                              │   LLM    │                                  │
│                              └────┬─────┘                                  │
│                                   │                                        │
│                                   ▼                                        │
│                              ┌──────────┐                                  │
│                              │  Output  │                                  │
│                              │  (JSON)  │                                  │
│                              └──────────┘                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Best For**: Simple rule sets, quick iteration  
**Limitations**: Context window limits, may miss complex interdependencies

### Pattern B: Iterative Refinement

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ITERATIVE REFINEMENT                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ Initial  │───▶│ Validate │───▶│ Identify │───▶│ Refine   │             │
│  │ Generate │    │          │    │ Gaps     │    │          │             │
│  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘             │
│                                                       │                    │
│       ▲                                               │                    │
│       │                                               │                    │
│       └───────────────────────────────────────────────┘                    │
│                        (Loop until complete)                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Steps**:
1. Generate initial rule set
2. Validate against schema and completeness checklist
3. Identify missing rules or errors
4. Generate refinements with specific focus
5. Merge and repeat

### Pattern C: Decomposed Generation (Divide & Conquer)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DECOMPOSED GENERATION                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    TASK DECOMPOSITION                                │   │
│  │                                                                      │   │
│  │  Full Task ──▶ ┌──────────────────────────────────────┐             │   │
│  │                │ 1. Income Tax Brackets               │             │   │
│  │                │ 2. Income Tax Deductions             │             │   │
│  │                │ 3. Social Security (Employee)        │             │   │
│  │                │ 4. Social Security (Employer)        │             │   │
│  │                │ 5. Other Employer Contributions      │             │   │
│  │                │ 6. Tax Credits                       │             │   │
│  │                └──────────────────────────────────────┘             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    PARALLEL GENERATION                               │   │
│  │                                                                      │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │   │
│  │  │ Task 1  │  │ Task 2  │  │ Task 3  │  │ Task 4  │  │ Task 5  │   │   │
│  │  │  LLM    │  │  LLM    │  │  LLM    │  │  LLM    │  │  LLM    │   │   │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘   │   │
│  │       │            │            │            │            │        │   │
│  │       └────────────┴────────────┼────────────┴────────────┘        │   │
│  │                                 │                                  │   │
│  │                                 ▼                                  │   │
│  │                          ┌───────────┐                             │   │
│  │                          │  MERGER   │                             │   │
│  │                          └─────┬─────┘                             │   │
│  │                                │                                   │   │
│  │                                ▼                                   │   │
│  │                          ┌───────────┐                             │   │
│  │                          │ Complete  │                             │   │
│  │                          │ Rule Set  │                             │   │
│  │                          └───────────┘                             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Pros**: Parallelizable, focused context per task, better for large rule sets  
**Cons**: Requires task taxonomy, merger complexity

### Pattern D: Multi-Agent Generation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MULTI-AGENT GENERATION                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      AGENT SYSTEM                                    │   │
│  │                                                                      │   │
│  │  ┌─────────────┐                                                    │   │
│  │  │ COORDINATOR │◀────────────────────────────────────────┐         │   │
│  │  │   AGENT     │                                          │         │   │
│  │  └──────┬──────┘                                          │         │   │
│  │         │                                                 │         │   │
│  │    Delegates                                         Reports        │   │
│  │         │                                                 │         │   │
│  │         ▼                                                 │         │   │
│  │  ┌──────────────────────────────────────────────────────┐│         │   │
│  │  │                                                      ││         │   │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐          ││         │   │
│  │  │  │ RESEARCH │  │EXTRACTOR │  │ GENERATOR│          ││         │   │
│  │  │  │  AGENT   │  │  AGENT   │  │  AGENT   │          ││         │   │
│  │  │  │          │  │          │  │          │          ││         │   │
│  │  │  │ • Find   │  │ • Parse  │  │ • Create │          ││         │   │
│  │  │  │   sources│  │   docs   │  │   rules  │          ││         │   │
│  │  │  │ • Verify │  │ • Extract│  │ • Format │          ││         │   │
│  │  │  │   auth   │  │   data   │  │   output │          ││         │   │
│  │  │  └──────────┘  └──────────┘  └──────────┘          ││         │   │
│  │  │                                                      ││         │   │
│  │  │  ┌──────────┐  ┌──────────┐                        ││         │   │
│  │  │  │ VALIDATOR│  │ REVIEWER │────────────────────────┘│         │   │
│  │  │  │  AGENT   │  │  AGENT   │                         │         │   │
│  │  │  │          │  │          │                         │         │   │
│  │  │  │ • Check  │  │ • Quality│                         │         │   │
│  │  │  │   schema │  │   review │                         │         │   │
│  │  │  │ • Test   │  │ • Suggest│                         │         │   │
│  │  │  │   cases  │  │   fixes  │                         │         │   │
│  │  │  └──────────┘  └──────────┘                         │         │   │
│  │  │                                                      │         │   │
│  │  └──────────────────────────────────────────────────────┘         │   │
│  │                                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Agent Frameworks

| Framework | Pros | Cons |
|-----------|------|------|
| **LangGraph** | Flexible, stateful, good control flow | Learning curve |
| **AutoGen** | Multi-agent conversations, Microsoft backed | Complex setup |
| **CrewAI** | Simple role-based agents | Less flexible |
| **AgentGPT** | Web-based, easy start | Limited customization |
| **Custom** | Full control | Development effort |

---

## Component 4: Output Generation & Validation

### Structured Output Options

#### Option 1: JSON Mode (OpenAI/Anthropic)

```python
response = client.chat.completions.create(
    model="gpt-4o",
    response_format={"type": "json_object"},
    messages=[...]
)
```

#### Option 2: Function Calling / Tool Use

```python
tools = [{
    "type": "function",
    "function": {
        "name": "create_tax_rule",
        "parameters": {
            "type": "object",
            "properties": {
                "rule_name": {"type": "string"},
                "threshold_min": {"type": "number"},
                "threshold_max": {"type": "number"},
                "rate": {"type": "number"}
            }
        }
    }
}]
```

#### Option 3: Grammar-Constrained Generation (Local LLMs)

```python
# Using Outlines library
from outlines import models, generate

schema = {
    "type": "object",
    "properties": {
        "rules": {"type": "array", "items": {...}}
    }
}

generator = generate.json(model, schema)
result = generator(prompt)
```

#### Option 4: Instructor Library (Pydantic Models)

```python
import instructor
from pydantic import BaseModel

class TaxRule(BaseModel):
    name: str
    min_threshold: float
    max_threshold: float
    rate: float

client = instructor.patch(OpenAI())
rule = client.chat.completions.create(
    model="gpt-4o",
    response_model=TaxRule,
    messages=[...]
)
```

### Validation Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         VALIDATION PIPELINE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ Generated│───▶│ Schema   │───▶│ Business │───▶│ Test     │             │
│  │ Output   │    │ Validation    │ Rules    │    │ Cases    │             │
│  └──────────┘    │ (JSON    │    │ Validation    │ Execution│             │
│                  │  Schema) │    │          │    │          │             │
│                  └──────────┘    └──────────┘    └────┬─────┘             │
│                                                       │                    │
│                                                       ▼                    │
│                                               ┌──────────────┐             │
│                                               │ Completeness │             │
│                                               │ Check        │             │
│                                               └──────┬───────┘             │
│                                                      │                     │
│                       ┌──────────────────────────────┼──────────────────┐  │
│                       │                              │                  │  │
│                       ▼                              ▼                  │  │
│                  ┌─────────┐                   ┌─────────┐              │  │
│                  │  PASS   │                   │  FAIL   │──▶ Feedback  │  │
│                  │         │                   │         │    Loop      │  │
│                  └─────────┘                   └─────────┘              │  │
│                                                                          │  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Component 5: Orchestration Frameworks

### Option A: LangChain / LangGraph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        LANGCHAIN/LANGGRAPH                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Pros:                                    Cons:                            │
│  • Large ecosystem                        • Abstraction overhead           │
│  • Many integrations                      • Fast-changing API              │
│  • Good documentation                     • Can be "magic"                 │
│  • LangGraph for complex flows            • Debugging complexity           │
│  • LangSmith for observability                                             │
│                                                                             │
│  Best For: Rapid prototyping, complex chains, team familiarity             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Option B: LlamaIndex

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           LLAMAINDEX                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Pros:                                    Cons:                            │
│  • RAG-focused                            • Less flexible than LangChain   │
│  • Excellent indexing                     • Smaller ecosystem              │
│  • Query engines                          • Opinionated design             │
│  • Document loaders                                                        │
│                                                                             │
│  Best For: Document-heavy RAG, indexing pipelines                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Option C: Haystack

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            HAYSTACK                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Pros:                                    Cons:                            │
│  • Production-focused                     • Smaller community              │
│  • Pipeline-based                         • Less flexible                  │
│  • Good for search                        • Steeper learning curve         │
│  • Enterprise features                                                     │
│                                                                             │
│  Best For: Production search systems, enterprise deployments               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Option D: Custom Implementation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        CUSTOM IMPLEMENTATION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Pros:                                    Cons:                            │
│  • Full control                           • Development effort             │
│  • No abstraction overhead                • Maintenance burden             │
│  • Optimized for use case                 • Reinventing the wheel          │
│  • No dependency risks                                                     │
│                                                                             │
│  Best For: Simple pipelines, specific requirements, long-term maintenance  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Recommended Configurations

### Configuration 1: Quick Start / MVP

| Component | Choice | Rationale |
|-----------|--------|-----------|
| LLM | GPT-4o or Claude 3.5 Sonnet | Best quality, easy API |
| Retrieval | Basic RAG | Simple, proven |
| Generation | Single-shot with JSON mode | Fast iteration |
| Framework | LangChain | Rapid development |
| Validation | JSON Schema | Sufficient for MVP |

### Configuration 2: Production / Enterprise

| Component | Choice | Rationale |
|-----------|--------|-----------|
| LLM | Azure OpenAI or Bedrock | Compliance, SLAs |
| Retrieval | Advanced RAG with re-ranking | Quality |
| Generation | Decomposed + iterative | Completeness |
| Framework | Custom or LangGraph | Control |
| Validation | Full pipeline with test cases | Reliability |

### Configuration 3: Maximum Privacy / Control

| Component | Choice | Rationale |
|-----------|--------|-----------|
| LLM | Self-hosted Llama 3.1 70B | Data stays internal |
| Retrieval | Agentic RAG | Quality without API dependency |
| Generation | Multi-agent with Instructor | Structured output |
| Framework | Custom | Full control |
| Validation | Comprehensive with human review | Critical domain |

---

## Cost Estimation Guide

### Per Country Rule Generation (Approximate)

| Configuration | Tokens (Input) | Tokens (Output) | Est. Cost |
|---------------|---------------|-----------------|-----------|
| MVP (GPT-4o) | ~100K | ~20K | $0.45 |
| Production (GPT-4o) | ~500K | ~100K | $2.25 |
| Complex (Claude Opus) | ~500K | ~100K | $15.00 |
| Self-Hosted | - | - | Infra cost only |

*Note: Costs vary significantly based on document volume, iteration count, and model choice.*

---

## Decision Framework

```
                                    START
                                      │
                                      ▼
                        ┌─────────────────────────┐
                        │ Data sensitivity high? │
                        └───────────┬─────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │ Yes                           │ No
                    ▼                               ▼
            ┌───────────────┐               ┌───────────────┐
            │ Self-hosted   │               │ Budget        │
            │ or Cloud      │               │ constrained?  │
            │ (Bedrock/     │               └───────┬───────┘
            │  Azure)       │                       │
            └───────────────┘           ┌───────────┴───────────┐
                                        │ Yes                   │ No
                                        ▼                       ▼
                                ┌───────────────┐       ┌───────────────┐
                                │ GPT-4o-mini   │       │ GPT-4o or     │
                                │ or Gemini     │       │ Claude 3.5    │
                                │ Flash         │       │ Sonnet        │
                                └───────────────┘       └───────────────┘
```

---

*Document created: January 2026*

