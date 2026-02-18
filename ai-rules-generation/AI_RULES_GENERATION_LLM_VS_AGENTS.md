# LLM vs Agents for Payroll Rules Generation

## Overview

This document compares two fundamental approaches for the AI component in payroll rules generation:

1. **Direct LLM**: Single or chained LLM calls with retrieval
2. **Agentic Systems**: Autonomous agents with tools, planning, and iteration

Both approaches use LLMs under the hood, but differ significantly in architecture, control flow, and capabilities.

---

## Approach 1: Direct LLM (RAG Pipeline)

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DIRECT LLM PIPELINE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │  Input   │───▶│ Retrieve │───▶│ Construct│───▶│   LLM    │             │
│  │  Query   │    │ Context  │    │  Prompt  │    │   Call   │             │
│  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘             │
│                                                       │                    │
│                                                       ▼                    │
│                                               ┌──────────────┐             │
│                                               │   Output     │             │
│                                               │   (Rules)    │             │
│                                               └──────────────┘             │
│                                                                             │
│  Characteristics:                                                          │
│  • Deterministic flow                                                      │
│  • Single retrieval pass                                                   │
│  • No self-correction                                                      │
│  • Predictable latency                                                     │
│  • Predictable cost                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### How It Works

```
Step 1: Query Construction
        "Generate France 2026 income tax rules"

Step 2: Retrieval (Fixed)
        → Retrieve top-K chunks from knowledge store
        → No ability to "ask for more" if insufficient

Step 3: Prompt Assembly
        System: "You are a payroll expert..."
        Context: [Retrieved documents]
        Task: "Generate rules in format X..."

Step 4: LLM Generation
        → Single forward pass
        → Output generated in one shot

Step 5: Output
        → Return result (may be incomplete or incorrect)
```

### Variants

| Variant | Description | Improvement |
|---------|-------------|-------------|
| **Basic RAG** | Single retrieval + single generation | Baseline |
| **Multi-Query RAG** | Generate multiple queries, merge results | Better recall |
| **Chain-of-Thought** | Ask LLM to reason step-by-step | Better accuracy |
| **Decomposed** | Split into sub-tasks, run in parallel | Better coverage |
| **Iterative Refinement** | Fixed loop: generate → validate → refine | Some self-correction |

---

## Approach 2: Agentic Systems

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AGENTIC SYSTEM                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         AGENT LOOP                                    │  │
│  │                                                                       │  │
│  │  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐       │  │
│  │  │  Input   │───▶│  PLAN    │───▶│  EXECUTE │───▶│ EVALUATE │       │  │
│  │  │  Goal    │    │          │    │  (Tools) │    │          │       │  │
│  │  └──────────┘    └──────────┘    └──────────┘    └────┬─────┘       │  │
│  │                       ▲                               │              │  │
│  │                       │         ┌─────────────────────┘              │  │
│  │                       │         │                                    │  │
│  │                       │         ▼                                    │  │
│  │                  ┌────┴────┐  ┌──────────┐                          │  │
│  │                  │ REPLAN  │◀─│  Goal    │                          │  │
│  │                  │         │  │  Met?    │                          │  │
│  │                  └─────────┘  └────┬─────┘                          │  │
│  │                                    │ Yes                             │  │
│  │                                    ▼                                 │  │
│  │                              ┌──────────┐                           │  │
│  │                              │  OUTPUT  │                           │  │
│  │                              └──────────┘                           │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                         TOOLS                                         │  │
│  │                                                                       │  │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐        │  │
│  │  │Search  │  │Query   │  │Validate│  │Calculate│ │ Write  │        │  │
│  │  │Docs    │  │Graph   │  │Schema  │  │Test     │ │ Rules  │        │  │
│  │  └────────┘  └────────┘  └────────┘  └────────┘  └────────┘        │  │
│  │                                                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  Characteristics:                                                          │
│  • Dynamic, goal-driven                                                    │
│  • Multiple retrieval passes                                               │
│  • Self-correcting                                                         │
│  • Variable latency                                                        │
│  • Variable cost                                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### How It Works

```
Step 1: Goal Understanding
        "Generate complete France 2026 gross-to-net rules"

Step 2: Planning (Dynamic)
        Agent thinks: "I need to:
        1. Find income tax regulations
        2. Find social security contributions
        3. Find employer burden rules
        4. Generate each category
        5. Validate completeness
        6. Cross-check calculations"

Step 3: Execution Loop
        → Call tool: search_documents("France income tax 2026")
        → Evaluate: "Found brackets but missing family quotient"
        → Call tool: search_documents("France quotient familial")
        → Evaluate: "Now have enough context"
        → Call tool: generate_rules(context, schema)
        → Call tool: validate_schema(rules)
        → Evaluate: "Missing CSG/CRDS rates"
        → Replan and continue...

Step 4: Completion Check
        → "Have I covered all required categories?"
        → "Do test cases pass?"
        → If no, continue loop

Step 5: Output
        → Return complete, validated result
```

---

## Head-to-Head Comparison

### Capability Comparison

| Capability | Direct LLM | Agentic |
|------------|-----------|---------|
| **Self-Correction** | ❌ None (unless hardcoded loop) | ✅ Built-in |
| **Dynamic Retrieval** | ❌ Fixed queries | ✅ Searches until satisfied |
| **Multi-Step Reasoning** | ⚠️ Limited by context | ✅ Can break down complex tasks |
| **Tool Use** | ❌ No | ✅ Yes (search, validate, calculate) |
| **Handle Ambiguity** | ❌ Guesses | ✅ Can ask clarifying questions |
| **Completeness Check** | ❌ No awareness | ✅ Can verify coverage |
| **Error Recovery** | ❌ Fails silently | ✅ Can retry with different approach |

### Operational Comparison

| Factor | Direct LLM | Agentic |
|--------|-----------|---------|
| **Latency** | Predictable (5-30s) | Variable (30s-10min) |
| **Cost** | Predictable | Variable (2-10x direct) |
| **Debugging** | Simple (single call) | Complex (trace agent decisions) |
| **Reliability** | High (deterministic) | Medium (can loop/fail) |
| **Observability** | Easy | Requires tooling |
| **Testing** | Straightforward | Complex (non-deterministic) |

### Quality Comparison (For Payroll Rules)

| Quality Metric | Direct LLM | Agentic |
|----------------|-----------|---------|
| **Accuracy (simple rules)** | 85-90% | 90-95% |
| **Accuracy (complex rules)** | 60-75% | 85-95% |
| **Completeness** | Often incomplete | Self-checks for gaps |
| **Consistency** | Can contradict itself | Can validate consistency |
| **Source Tracing** | Poor | Can verify sources |

---

## Payroll Rules Generation: Specific Analysis

### Why Direct LLM Falls Short (Your 70-80% Result)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DIRECT LLM FAILURE MODES                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. INCOMPLETE RETRIEVAL                                                   │
│     ───────────────────────                                                │
│     Query: "France social security"                                        │
│     Retrieved: General overview                                            │
│     Missed: Specific URSSAF rate tables, ceiling values                   │
│                                                                             │
│     Agent would: Search again for "URSSAF 2026 rates",                    │
│                  "plafond sécurité sociale 2026"                          │
│                                                                             │
│  2. CONTEXT OVERFLOW                                                       │
│     ─────────────────────                                                  │
│     Problem: Can't fit all regulations in context window                  │
│     Result: Truncates, loses important details                            │
│                                                                             │
│     Agent would: Process in chunks, maintain state across calls           │
│                                                                             │
│  3. NO CROSS-VALIDATION                                                    │
│     ─────────────────────                                                  │
│     Problem: Generated 9.2% CSG rate (should be split: 6.8% + 2.4%)      │
│     Result: Incorrect rule, no detection                                  │
│                                                                             │
│     Agent would: Validate against source, check math                      │
│                                                                             │
│  4. MISSING DEPENDENCIES                                                   │
│     ────────────────────────                                               │
│     Problem: Generated retirement contribution without ceiling reference  │
│     Result: Rule incomplete, will fail in payroll engine                  │
│                                                                             │
│     Agent would: Check schema requirements, query for missing fields      │
│                                                                             │
│  5. TEMPORAL CONFUSION                                                     │
│     ────────────────────                                                   │
│     Problem: Mixed 2025 and 2026 rates in output                          │
│     Result: Incorrect rules                                                │
│                                                                             │
│     Agent would: Explicitly verify dates for each extracted value         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why Agents Are Better for This Use Case

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              AGENT ADVANTAGES FOR PAYROLL RULES                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. EXHAUSTIVE SEARCH                                                      │
│     ─────────────────────                                                  │
│     Agent can systematically search for each required category:           │
│     □ Income tax brackets        □ Social security employee              │
│     □ Income tax deductions      □ Social security employer              │
│     □ Tax credits                □ Other employer contributions          │
│                                                                             │
│  2. SCHEMA-DRIVEN GENERATION                                               │
│     ──────────────────────────                                             │
│     Agent knows the target schema, can check:                             │
│     "Does my output have all required fields?"                            │
│     "Is the data type correct?"                                           │
│     "Are there any null values that shouldn't be?"                        │
│                                                                             │
│  3. VALIDATION LOOP                                                        │
│     ────────────────────                                                   │
│     Agent can run test cases:                                              │
│     "For €50,000 salary, does my tax calculation match expected?"         │
│     If not: "Which rule is wrong? Let me re-check that section."         │
│                                                                             │
│  4. SOURCE VERIFICATION                                                    │
│     ──────────────────────                                                 │
│     Agent can trace each value back:                                      │
│     "I extracted 11% tax rate - let me verify this is from 2026 doc"     │
│                                                                             │
│  5. DEPENDENCY RESOLUTION                                                  │
│     ────────────────────────                                               │
│     Agent understands rule dependencies:                                  │
│     "CSG base depends on gross salary - is that defined?"                │
│     "Retirement ceiling references PASS - is PASS value included?"       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Cost-Benefit Analysis

### Scenario: Generate Rules for One Country

| Metric | Direct LLM | Agentic | Notes |
|--------|-----------|---------|-------|
| **LLM Calls** | 1-3 | 10-30 | Agent iterates |
| **Tokens (Input)** | ~100K | ~300-500K | Multiple retrievals |
| **Tokens (Output)** | ~20K | ~50-100K | Intermediate outputs |
| **Cost (GPT-4o)** | ~$0.50 | ~$2-5 | Variable |
| **Latency** | 30-60s | 3-10 min | Depends on iterations |
| **Accuracy** | 70-80% | 90-95% | Significant improvement |
| **Human Review Time** | High (fix 20-30% errors) | Low (verify, edge cases) | Major time savings |

### Total Cost of Ownership

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TOTAL COST COMPARISON (Per Country)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  DIRECT LLM:                                                               │
│  ──────────────                                                            │
│  LLM Cost:                           $0.50                                 │
│  Human Review (fix 25% errors):      $50-100 (2-4 hours @ $25/hr)         │
│  Re-runs after fixes:                $1-2                                  │
│  ─────────────────────────────────────────────                             │
│  TOTAL:                              $52-103                               │
│                                                                             │
│  AGENTIC:                                                                  │
│  ──────────────                                                            │
│  LLM Cost:                           $3-5                                  │
│  Human Review (verify 5% edge cases): $12-25 (0.5-1 hour @ $25/hr)        │
│  Re-runs (rare):                     $0-1                                  │
│  ─────────────────────────────────────────────                             │
│  TOTAL:                              $15-31                                │
│                                                                             │
│  WINNER: Agentic (3-5x cheaper total cost)                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Production Considerations (AWS)

### Direct LLM on AWS

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DIRECT LLM - AWS ARCHITECTURE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                 │
│  │ API GW  │───▶│ Lambda  │───▶│ Bedrock │───▶│ S3      │                 │
│  │         │    │         │    │ (Claude)│    │ (Output)│                 │
│  └─────────┘    └────┬────┘    └─────────┘    └─────────┘                 │
│                      │                                                     │
│                      ▼                                                     │
│                 ┌─────────┐                                                │
│                 │OpenSearch                                                │
│                 │(Vectors)│                                                │
│                 └─────────┘                                                │
│                                                                             │
│  Pros: Simple, serverless, predictable                                    │
│  Cons: Limited accuracy, no self-correction                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Agentic on AWS

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AGENTIC - AWS ARCHITECTURE                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Option A: AWS Bedrock Agents                                              │
│  ────────────────────────────────                                          │
│  ┌─────────┐    ┌──────────────┐    ┌─────────┐                           │
│  │ API GW  │───▶│ Bedrock      │───▶│ Lambda  │ (Tool execution)          │
│  │         │    │ Agents       │    │ Functions│                          │
│  └─────────┘    └──────┬───────┘    └─────────┘                           │
│                        │                                                   │
│                        ▼                                                   │
│                 ┌─────────────┐                                            │
│                 │ Knowledge   │                                            │
│                 │ Base (RAG)  │                                            │
│                 └─────────────┘                                            │
│                                                                             │
│  Option B: Step Functions + Bedrock                                        │
│  ──────────────────────────────────                                        │
│  ┌─────────┐    ┌──────────────┐    ┌─────────┐                           │
│  │ API GW  │───▶│ Step         │───▶│ Lambda  │ (Each step)               │
│  │         │    │ Functions    │    │ Tasks   │                           │
│  └─────────┘    └──────┬───────┘    └────┬────┘                           │
│                        │                 │                                 │
│                        │    ┌────────────┘                                 │
│                        ▼    ▼                                              │
│                 ┌─────────────┐    ┌─────────┐                            │
│                 │ Bedrock     │    │ Neptune │                            │
│                 │ (Claude)    │    │ (Graph) │                            │
│                 └─────────────┘    └─────────┘                            │
│                                                                             │
│  Option C: ECS/EKS + Custom Agent                                         │
│  ────────────────────────────────                                          │
│  ┌─────────┐    ┌──────────────┐    ┌─────────┐                           │
│  │ API GW  │───▶│ ECS/EKS      │───▶│ Bedrock │                           │
│  │         │    │ (LangGraph)  │    │         │                           │
│  └─────────┘    └──────┬───────┘    └─────────┘                           │
│                        │                                                   │
│                        ▼                                                   │
│                 ┌─────────────┐                                            │
│                 │ Neptune +   │                                            │
│                 │ OpenSearch  │                                            │
│                 └─────────────┘                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Recommendation

### For Payroll Rules Generation: **Agentic Approach**

| Factor | Verdict |
|--------|---------|
| **Accuracy requirement** | High (95%+) → Agents |
| **Rule complexity** | High (interdependencies) → Agents |
| **Self-correction need** | Critical → Agents |
| **Cost sensitivity** | TCO favors Agents (less human review) |
| **Timeline** | Agents take longer to build but pay off |

### When Direct LLM Would Be Acceptable

- Simple, well-structured regulations
- Human review is cheap/available
- Speed more important than accuracy
- Prototype/exploration phase

### Migration Path

```
Phase 1: Direct LLM (Validate approach)
         → Baseline accuracy, understand failure modes

Phase 2: Add validation loop (Iterative refinement)
         → Improves accuracy to 80-85%

Phase 3: Full agentic (Production)
         → Target 95%+ accuracy with self-correction
```

---

## Next Steps

See companion document: **AI_RULES_GENERATION_AGENT_ARCHITECTURE_OPTIONS.md** for detailed agent implementation options on AWS.

---

*Document created: January 2026*

