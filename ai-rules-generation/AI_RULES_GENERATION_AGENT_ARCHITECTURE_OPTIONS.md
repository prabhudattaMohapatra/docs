# Agent Architecture Options for Payroll Rules Generation

## Overview

This document details production-ready agent architecture options for generating payroll rules on AWS infrastructure. All options assume:

- AWS as the cloud platform
- Need for 95%+ accuracy
- Knowledge store (GraphRAG) as the data layer
- Production reliability requirements

---

## Option 1: AWS Bedrock Agents

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AWS BEDROCK AGENTS                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         AWS ACCOUNT                                  │   │
│  │                                                                      │   │
│  │  ┌──────────┐                                                       │   │
│  │  │ API      │                                                       │   │
│  │  │ Gateway  │                                                       │   │
│  │  └────┬─────┘                                                       │   │
│  │       │                                                             │   │
│  │       ▼                                                             │   │
│  │  ┌────────────────────────────────────────────────────────────┐    │   │
│  │  │                    BEDROCK AGENT                            │    │   │
│  │  │                                                             │    │   │
│  │  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │    │   │
│  │  │  │ Foundation  │    │ Agent       │    │ Action      │    │    │   │
│  │  │  │ Model       │    │ Instructions│    │ Groups      │    │    │   │
│  │  │  │ (Claude 3)  │    │ (Prompt)    │    │             │    │    │   │
│  │  │  └─────────────┘    └─────────────┘    └──────┬──────┘    │    │   │
│  │  │                                               │           │    │   │
│  │  └───────────────────────────────────────────────┼───────────┘    │   │
│  │                                                  │                │   │
│  │       ┌──────────────────────────────────────────┼────────────┐   │   │
│  │       │                                          │            │   │   │
│  │       ▼                                          ▼            │   │   │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐       │   │   │
│  │  │ Knowledge   │    │ Lambda      │    │ Lambda      │       │   │   │
│  │  │ Base        │    │ (Search     │    │ (Validate   │       │   │   │
│  │  │ (OpenSearch │    │  Graph)     │    │  Rules)     │       │   │   │
│  │  │  Serverless)│    │             │    │             │       │   │   │
│  │  └─────────────┘    └──────┬──────┘    └──────┬──────┘       │   │   │
│  │                            │                  │               │   │   │
│  │                            ▼                  ▼               │   │   │
│  │                     ┌─────────────┐    ┌─────────────┐       │   │   │
│  │                     │ Neptune     │    │ S3          │       │   │   │
│  │                     │ (Graph DB)  │    │ (Output)    │       │   │   │
│  │                     └─────────────┘    └─────────────┘       │   │   │
│  │                                                               │   │   │
│  └─────────────────────────────────────────────────────────────────┘   │   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Components

| Component | AWS Service | Purpose |
|-----------|-------------|---------|
| Agent Runtime | Bedrock Agents | Orchestration, planning, tool selection |
| Foundation Model | Claude 3 Sonnet/Opus (Bedrock) | Reasoning, generation |
| Knowledge Base | Bedrock Knowledge Bases | RAG retrieval (uses OpenSearch Serverless) |
| Graph Queries | Lambda + Neptune | Structured data retrieval |
| Validation | Lambda | Schema validation, test execution |
| Output Storage | S3 | Generated rules storage |

### Action Groups (Tools)

```yaml
Action Groups:
  - name: SearchRegulations
    description: Search regulatory documents by topic
    lambda: search-regulations-lambda
    parameters:
      - country: string (required)
      - topic: string (required)  # income_tax, social_security, etc.
      - year: integer (required)
    
  - name: QueryKnowledgeGraph
    description: Query structured regulatory data from graph
    lambda: query-graph-lambda
    parameters:
      - query_type: enum [tax_brackets, contributions, ceilings]
      - country: string (required)
      - filters: object (optional)
    
  - name: GenerateRules
    description: Generate rules in target format
    lambda: generate-rules-lambda
    parameters:
      - category: string (required)
      - context: string (required)
      - output_schema: string (required)
    
  - name: ValidateRules
    description: Validate generated rules against schema and test cases
    lambda: validate-rules-lambda
    parameters:
      - rules: object (required)
      - validation_type: enum [schema, test_cases, completeness]
    
  - name: SaveOutput
    description: Save final rules to S3
    lambda: save-output-lambda
    parameters:
      - rules: object (required)
      - country: string (required)
      - version: string (required)
```

### Agent Instructions (System Prompt)

```
You are a payroll regulations expert assistant. Your goal is to generate 
complete and accurate gross-to-net calculation rules for specified countries.

PROCESS:
1. Understand the request (country, year, output format)
2. Search for all relevant regulatory categories:
   - Income tax (brackets, deductions, credits)
   - Social security (employee contributions)
   - Social security (employer contributions)  
   - Other employer burden
3. For each category:
   a. Search documents for context
   b. Query knowledge graph for structured data
   c. Generate rules in specified format
   d. Validate against schema
4. Check completeness - ensure all categories covered
5. Run test cases to verify calculations
6. If validation fails, identify issue and retry
7. Save final output

IMPORTANT:
- Always verify dates - only use current year regulations
- Cross-reference multiple sources when possible
- Flag any uncertainties for human review
- Include source references for all extracted values
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Fully managed, serverless | ❌ Limited control over agent loop |
| ✅ Native AWS integration | ❌ Debugging is challenging |
| ✅ Built-in RAG with Knowledge Bases | ❌ Action group limitations |
| ✅ Automatic scaling | ❌ Less flexible than custom agents |
| ✅ Lower operational overhead | ❌ Bedrock-specific, some vendor lock-in |
| ✅ CloudWatch integration | ❌ Limited model choices (Bedrock only) |

### Cost Estimate

| Component | Pricing | Est. Monthly (10 countries) |
|-----------|---------|----------------------------|
| Bedrock Claude 3 Sonnet | $3/$15 per 1M tokens | $50-150 |
| Bedrock Agents | $0.80 per 1K steps | $20-50 |
| Knowledge Base | $0.35/hr crawling + storage | $20-30 |
| Lambda | Compute time | $5-10 |
| Neptune | Instance hours | $200-400 |
| **Total** | | **$300-650/month** |

---

## Option 2: AWS Step Functions + Bedrock

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   STEP FUNCTIONS + BEDROCK ARCHITECTURE                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐                                                              │
│  │ API      │                                                              │
│  │ Gateway  │                                                              │
│  └────┬─────┘                                                              │
│       │                                                                    │
│       ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    STEP FUNCTIONS STATE MACHINE                      │   │
│  │                                                                      │   │
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐          │   │
│  │  │ START   │───▶│ PLAN    │───▶│ EXTRACT │───▶│ ITERATE │          │   │
│  │  │         │    │         │    │ LOOP    │    │ CATEGORIES         │   │
│  │  └─────────┘    └─────────┘    └────┬────┘    └────┬────┘          │   │
│  │                                     │              │                │   │
│  │                      ┌──────────────┘              │                │   │
│  │                      │                             │                │   │
│  │                      ▼                             ▼                │   │
│  │                 ┌─────────┐                  ┌─────────┐            │   │
│  │                 │ For Each│                  │ GENERATE│            │   │
│  │                 │ Category│                  │ RULES   │            │   │
│  │                 │         │                  │         │            │   │
│  │                 │ ┌─────┐ │                  └────┬────┘            │   │
│  │                 │ │Search│ │                      │                 │   │
│  │                 │ └──┬──┘ │                       │                 │   │
│  │                 │    ▼    │                       ▼                 │   │
│  │                 │ ┌─────┐ │                  ┌─────────┐            │   │
│  │                 │ │Query│ │                  │ VALIDATE│            │   │
│  │                 │ │Graph│ │                  │         │            │   │
│  │                 │ └──┬──┘ │                  └────┬────┘            │   │
│  │                 │    ▼    │                       │                 │   │
│  │                 │ ┌─────┐ │         ┌─────────────┴─────────────┐   │   │
│  │                 │ │Extr.│ │         │                           │   │   │
│  │                 │ │Data │ │         ▼                           ▼   │   │
│  │                 │ └─────┘ │    ┌─────────┐                ┌─────────┐   │
│  │                 └─────────┘    │ PASS    │                │ FAIL    │   │
│  │                                │ ──────▶ │                │ ──────▶ │   │
│  │                                │ SAVE    │                │ RETRY   │   │
│  │                                └─────────┘                └────┬────┘   │
│  │                                                                │    │   │
│  │                                     ◀──────────────────────────┘    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│       │              │              │              │                        │
│       ▼              ▼              ▼              ▼                        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                  │
│  │ Lambda  │    │ Lambda  │    │ Lambda  │    │ Lambda  │                  │
│  │ (Plan)  │    │ (Search)│    │(Generate│    │(Validate│                  │
│  │         │    │         │    │         │    │         │                  │
│  └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘                  │
│       │              │              │              │                        │
│       ▼              ▼              ▼              ▼                        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                  │
│  │ Bedrock │    │ Neptune │    │ Bedrock │    │ Test    │                  │
│  │ (LLM)   │    │OpenSearch    │ (LLM)   │    │ Runner  │                  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### State Machine Definition (Simplified)

```json
{
  "Comment": "Payroll Rules Generation Agent",
  "StartAt": "Plan",
  "States": {
    "Plan": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:plan-function",
      "Next": "ExtractCategories"
    },
    "ExtractCategories": {
      "Type": "Map",
      "ItemsPath": "$.categories",
      "Iterator": {
        "StartAt": "SearchDocuments",
        "States": {
          "SearchDocuments": {
            "Type": "Task",
            "Resource": "arn:aws:lambda:...:search-function",
            "Next": "QueryGraph"
          },
          "QueryGraph": {
            "Type": "Task",
            "Resource": "arn:aws:lambda:...:query-graph-function",
            "Next": "ExtractData"
          },
          "ExtractData": {
            "Type": "Task",
            "Resource": "arn:aws:lambda:...:extract-function",
            "End": true
          }
        }
      },
      "Next": "GenerateRules"
    },
    "GenerateRules": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:generate-function",
      "Next": "Validate"
    },
    "Validate": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:validate-function",
      "Next": "CheckValidation"
    },
    "CheckValidation": {
      "Type": "Choice",
      "Choices": [
        {
          "Variable": "$.validation.passed",
          "BooleanEquals": true,
          "Next": "SaveOutput"
        },
        {
          "Variable": "$.retryCount",
          "NumericLessThan": 3,
          "Next": "IdentifyIssues"
        }
      ],
      "Default": "FailWithErrors"
    },
    "IdentifyIssues": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:identify-issues-function",
      "Next": "RetryExtraction"
    },
    "RetryExtraction": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:retry-extraction-function",
      "Next": "GenerateRules"
    },
    "SaveOutput": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:save-function",
      "End": true
    },
    "FailWithErrors": {
      "Type": "Fail",
      "Error": "ValidationFailed",
      "Cause": "Max retries exceeded"
    }
  }
}
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Full control over workflow | ❌ More development effort |
| ✅ Explicit state management | ❌ State machine complexity |
| ✅ Built-in retry/error handling | ❌ Less "intelligent" routing |
| ✅ Excellent observability (X-Ray) | ❌ Harder to handle dynamic replanning |
| ✅ Cost-effective at scale | ❌ Fixed flow (not truly agentic) |
| ✅ Long-running workflow support | ❌ More Lambda functions to maintain |

### Cost Estimate

| Component | Pricing | Est. Monthly (10 countries) |
|-----------|---------|----------------------------|
| Step Functions | $25 per 1M state transitions | $10-20 |
| Bedrock Claude 3 | $3/$15 per 1M tokens | $50-150 |
| Lambda | Compute time | $10-20 |
| Neptune | Instance hours | $200-400 |
| OpenSearch Serverless | OCU hours | $50-100 |
| **Total** | | **$320-690/month** |

---

## Option 3: Custom Agent on ECS/EKS (LangGraph)

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   CUSTOM AGENT ON ECS (LANGGRAPH)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐                                                              │
│  │ API      │                                                              │
│  │ Gateway  │                                                              │
│  └────┬─────┘                                                              │
│       │                                                                    │
│       ▼                                                                    │
│  ┌──────────┐                                                              │
│  │ ALB      │                                                              │
│  └────┬─────┘                                                              │
│       │                                                                    │
│       ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ECS FARGATE CLUSTER                               │   │
│  │                                                                      │   │
│  │  ┌───────────────────────────────────────────────────────────────┐  │   │
│  │  │                 AGENT SERVICE (Python)                         │  │   │
│  │  │                                                                │  │   │
│  │  │  ┌─────────────────────────────────────────────────────────┐  │  │   │
│  │  │  │                    LANGGRAPH AGENT                       │  │  │   │
│  │  │  │                                                          │  │  │   │
│  │  │  │   ┌──────────┐                                          │  │  │   │
│  │  │  │   │ SUPERVISOR│                                          │  │  │   │
│  │  │  │   │  NODE     │                                          │  │  │   │
│  │  │  │   └─────┬─────┘                                          │  │  │   │
│  │  │  │         │                                                │  │  │   │
│  │  │  │    ┌────┴────┬────────────┬────────────┐                │  │  │   │
│  │  │  │    │         │            │            │                │  │  │   │
│  │  │  │    ▼         ▼            ▼            ▼                │  │  │   │
│  │  │  │ ┌──────┐ ┌──────┐   ┌──────┐    ┌──────┐               │  │  │   │
│  │  │  │ │SEARCH│ │EXTRACT   │GENERATE   │VALIDATE              │  │  │   │
│  │  │  │ │ NODE │ │ NODE │   │ NODE │    │ NODE │               │  │  │   │
│  │  │  │ └──────┘ └──────┘   └──────┘    └──────┘               │  │  │   │
│  │  │  │                                                          │  │  │   │
│  │  │  │   State: { messages, context, rules, validation }       │  │  │   │
│  │  │  │                                                          │  │  │   │
│  │  │  └─────────────────────────────────────────────────────────┘  │  │   │
│  │  │                                                                │  │   │
│  │  │  Tools:                                                        │  │   │
│  │  │  • search_documents()   • query_graph()                       │  │   │
│  │  │  • generate_rules()     • validate_schema()                   │  │   │
│  │  │  • run_test_cases()     • save_output()                       │  │   │
│  │  │                                                                │  │   │
│  │  └───────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│       │              │              │              │                        │
│       ▼              ▼              ▼              ▼                        │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐                  │
│  │ Bedrock │    │ Neptune │    │OpenSearch    │ S3      │                  │
│  │ (LLM)   │    │ (Graph) │    │(Vectors)│    │(Output) │                  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘                  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    OBSERVABILITY                                     │   │
│  │                                                                      │   │
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐                          │   │
│  │  │CloudWatch    │ X-Ray   │    │LangSmith│ (Optional)               │   │
│  │  │ Logs    │    │ Tracing │    │         │                          │   │
│  │  └─────────┘    └─────────┘    └─────────┘                          │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### LangGraph Implementation

```python
from langgraph.graph import StateGraph, END
from langgraph.prebuilt import ToolExecutor
from langchain_aws import ChatBedrock
from pydantic import BaseModel
from typing import List, Dict, Any, Annotated
import operator

# State Definition
class AgentState(BaseModel):
    country: str
    year: int
    messages: Annotated[List[Dict], operator.add]
    extracted_data: Dict[str, Any] = {}
    generated_rules: Dict[str, Any] = {}
    validation_result: Dict[str, Any] = {}
    retry_count: int = 0
    current_category: str = ""
    categories_completed: List[str] = []

# Tools
tools = [
    search_documents,      # Search vector store
    query_knowledge_graph, # Query Neptune
    extract_structured_data,
    generate_rules_for_category,
    validate_rules,
    run_test_cases,
    save_output
]

# Nodes
def supervisor_node(state: AgentState) -> AgentState:
    """Decides next action based on current state"""
    llm = ChatBedrock(model_id="anthropic.claude-3-sonnet")
    
    # Determine what to do next
    if not state.extracted_data:
        return {"current_category": "income_tax", "next": "search"}
    elif state.validation_result.get("passed"):
        return {"next": "save"}
    elif state.retry_count >= 3:
        return {"next": "fail"}
    else:
        # Ask LLM what to do
        response = llm.invoke(
            f"Current state: {state}. What should we do next?"
        )
        return {"next": parse_next_action(response)}

def search_node(state: AgentState) -> AgentState:
    """Search for documents related to current category"""
    results = search_documents(
        country=state.country,
        category=state.current_category,
        year=state.year
    )
    return {"messages": [{"role": "search", "content": results}]}

def extract_node(state: AgentState) -> AgentState:
    """Extract structured data from search results"""
    llm = ChatBedrock(model_id="anthropic.claude-3-sonnet")
    
    # Also query knowledge graph
    graph_data = query_knowledge_graph(
        country=state.country,
        category=state.current_category
    )
    
    # Use LLM to extract and reconcile
    extracted = llm.invoke(
        f"Extract {state.current_category} data from: "
        f"Documents: {state.messages[-1]['content']}"
        f"Graph data: {graph_data}"
    )
    
    return {
        "extracted_data": {
            **state.extracted_data,
            state.current_category: extracted
        }
    }

def generate_node(state: AgentState) -> AgentState:
    """Generate rules from extracted data"""
    llm = ChatBedrock(model_id="anthropic.claude-3-sonnet")
    
    rules = llm.invoke(
        f"Generate payroll rules for {state.country} "
        f"from data: {state.extracted_data}. "
        f"Use schema: {OUTPUT_SCHEMA}"
    )
    
    return {"generated_rules": rules}

def validate_node(state: AgentState) -> AgentState:
    """Validate generated rules"""
    schema_valid = validate_schema(state.generated_rules)
    test_results = run_test_cases(state.generated_rules, state.country)
    
    passed = schema_valid and test_results["all_passed"]
    
    return {
        "validation_result": {
            "passed": passed,
            "schema_valid": schema_valid,
            "test_results": test_results
        }
    }

def should_continue(state: AgentState) -> str:
    """Routing function"""
    if state.validation_result.get("passed"):
        return "save"
    elif state.retry_count >= 3:
        return "fail"
    elif not all_categories_done(state):
        return "next_category"
    else:
        return "retry"

# Build Graph
workflow = StateGraph(AgentState)

# Add nodes
workflow.add_node("supervisor", supervisor_node)
workflow.add_node("search", search_node)
workflow.add_node("extract", extract_node)
workflow.add_node("generate", generate_node)
workflow.add_node("validate", validate_node)
workflow.add_node("save", save_node)

# Add edges
workflow.set_entry_point("supervisor")
workflow.add_edge("supervisor", "search")
workflow.add_edge("search", "extract")
workflow.add_edge("extract", "supervisor")  # Back to supervisor for next category
workflow.add_conditional_edges(
    "generate",
    should_continue,
    {
        "validate": "validate",
    }
)
workflow.add_conditional_edges(
    "validate",
    should_continue,
    {
        "save": "save",
        "retry": "supervisor",
        "fail": END
    }
)
workflow.add_edge("save", END)

# Compile
agent = workflow.compile()
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Maximum flexibility | ❌ Highest development effort |
| ✅ True agentic behavior | ❌ Operational complexity |
| ✅ Custom logic anywhere | ❌ Need to handle scaling |
| ✅ Full observability control | ❌ Container management |
| ✅ Model agnostic (can use any LLM) | ❌ More infrastructure to maintain |
| ✅ LangSmith integration for debugging | ❌ Cold start latency |
| ✅ Easy local development | |

### Cost Estimate

| Component | Pricing | Est. Monthly (10 countries) |
|-----------|---------|----------------------------|
| ECS Fargate | vCPU + Memory hours | $50-100 |
| Bedrock Claude 3 | $3/$15 per 1M tokens | $50-150 |
| Neptune | Instance hours | $200-400 |
| OpenSearch Serverless | OCU hours | $50-100 |
| ALB | LCU hours | $20-30 |
| LangSmith (optional) | Team plan | $0-400 |
| **Total** | | **$370-1180/month** |

---

## Option 4: Multi-Agent System (Specialized Agents)

### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MULTI-AGENT SYSTEM                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                      ORCHESTRATOR AGENT                               │  │
│  │                                                                       │  │
│  │  • Receives initial request                                          │  │
│  │  • Creates execution plan                                            │  │
│  │  • Delegates to specialist agents                                    │  │
│  │  • Aggregates results                                                │  │
│  │  • Handles failures and retries                                      │  │
│  │                                                                       │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
│                                     │                                       │
│           ┌─────────────────────────┼─────────────────────────┐            │
│           │                         │                         │            │
│           ▼                         ▼                         ▼            │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐        │
│  │ RESEARCH AGENT  │    │ EXTRACTION      │    │ GENERATION      │        │
│  │                 │    │ AGENT           │    │ AGENT           │        │
│  │ Responsibilities│    │                 │    │                 │        │
│  │ • Find sources  │    │ Responsibilities│    │ Responsibilities│        │
│  │ • Verify auth   │    │ • Parse docs    │    │ • Create rules  │        │
│  │ • Identify gaps │    │ • Extract data  │    │ • Format output │        │
│  │ • Cross-ref     │    │ • Structure     │    │ • Handle edge   │        │
│  │                 │    │   information   │    │   cases         │        │
│  │ Tools:          │    │                 │    │                 │        │
│  │ • web_search    │    │ Tools:          │    │ Tools:          │        │
│  │ • doc_search    │    │ • query_graph   │    │ • generate_json │        │
│  │ • verify_source │    │ • extract_table │    │ • apply_template│        │
│  │                 │    │ • parse_pdf     │    │ • merge_rules   │        │
│  └────────┬────────┘    └────────┬────────┘    └────────┬────────┘        │
│           │                      │                      │                  │
│           └──────────────────────┼──────────────────────┘                  │
│                                  │                                         │
│                                  ▼                                         │
│                    ┌─────────────────────┐                                 │
│                    │  VALIDATION AGENT   │                                 │
│                    │                     │                                 │
│                    │  Responsibilities   │                                 │
│                    │  • Schema check     │                                 │
│                    │  • Math verification│                                 │
│                    │  • Test execution   │                                 │
│                    │  • Completeness     │                                 │
│                    │  • Source tracing   │                                 │
│                    │                     │                                 │
│                    │  Tools:             │                                 │
│                    │  • validate_schema  │                                 │
│                    │  • run_calc_tests   │                                 │
│                    │  • check_coverage   │                                 │
│                    │  • trace_sources    │                                 │
│                    └──────────┬──────────┘                                 │
│                               │                                            │
│                               ▼                                            │
│                    ┌─────────────────────┐                                 │
│                    │   REVIEW AGENT      │                                 │
│                    │   (Human-in-loop)   │                                 │
│                    │                     │                                 │
│                    │  • Flag uncertainties                                 │
│                    │  • Request human input                                │
│                    │  • Apply corrections │                                │
│                    │                     │                                 │
│                    └─────────────────────┘                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Implementation on AWS

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   MULTI-AGENT AWS IMPLEMENTATION                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐                                                              │
│  │ API GW   │                                                              │
│  └────┬─────┘                                                              │
│       │                                                                    │
│       ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ECS CLUSTER                                       │   │
│  │                                                                      │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐│   │
│  │  │Orchestrator │  │ Research    │  │ Extraction  │  │ Generation  ││   │
│  │  │ Service     │  │ Service     │  │ Service     │  │ Service     ││   │
│  │  │             │  │             │  │             │  │             ││   │
│  │  │ (LangGraph) │  │ (LangGraph) │  │ (LangGraph) │  │ (LangGraph) ││   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘│   │
│  │         │                │                │                │       │   │
│  └─────────┼────────────────┼────────────────┼────────────────┼───────┘   │
│            │                │                │                │            │
│            └────────────────┴────────┬───────┴────────────────┘            │
│                                      │                                      │
│                                      ▼                                      │
│                              ┌───────────────┐                              │
│                              │   SQS/SNS     │  (Agent Communication)       │
│                              │   Message Bus │                              │
│                              └───────────────┘                              │
│                                      │                                      │
│       ┌──────────────────────────────┼──────────────────────────────┐      │
│       │                              │                              │      │
│       ▼                              ▼                              ▼      │
│  ┌─────────┐                   ┌─────────┐                    ┌─────────┐  │
│  │ Bedrock │                   │ Neptune │                    │ S3      │  │
│  │         │                   │         │                    │         │  │
│  └─────────┘                   └─────────┘                    └─────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Agent Communication Protocol

```python
# Message format between agents
class AgentMessage(BaseModel):
    id: str
    from_agent: str
    to_agent: str
    message_type: str  # "request", "response", "error"
    payload: Dict[str, Any]
    correlation_id: str
    timestamp: datetime

# Example flow
messages = [
    # Orchestrator -> Research
    AgentMessage(
        from_agent="orchestrator",
        to_agent="research",
        message_type="request",
        payload={
            "task": "find_regulations",
            "country": "France",
            "categories": ["income_tax", "social_security"],
            "year": 2026
        }
    ),
    
    # Research -> Orchestrator
    AgentMessage(
        from_agent="research",
        to_agent="orchestrator", 
        message_type="response",
        payload={
            "status": "complete",
            "sources_found": 12,
            "documents": [...],
            "confidence": 0.95
        }
    ),
    
    # Orchestrator -> Extraction
    AgentMessage(
        from_agent="orchestrator",
        to_agent="extraction",
        message_type="request",
        payload={
            "task": "extract_data",
            "documents": [...],
            "schema": EXTRACTION_SCHEMA
        }
    ),
    # ... continues
]
```

### Pros & Cons

| Pros | Cons |
|------|------|
| ✅ Specialized agents = better quality | ❌ Highest complexity |
| ✅ Parallel processing possible | ❌ Agent coordination overhead |
| ✅ Clear separation of concerns | ❌ More services to deploy/maintain |
| ✅ Easier to improve individual agents | ❌ Debugging across agents is hard |
| ✅ Human-in-loop natural fit | ❌ Message passing latency |
| ✅ Scales well for multiple countries | ❌ Cost multiplier (multiple LLM calls) |

### Cost Estimate

| Component | Pricing | Est. Monthly (10 countries) |
|-----------|---------|----------------------------|
| ECS Fargate (4 services) | vCPU + Memory | $100-200 |
| Bedrock Claude 3 | $3/$15 per 1M tokens | $100-300 |
| Neptune | Instance hours | $200-400 |
| OpenSearch Serverless | OCU hours | $50-100 |
| SQS/SNS | Message volume | $5-10 |
| **Total** | | **$455-1010/month** |

---

## Comparison Matrix

| Criteria | Bedrock Agents | Step Functions | Custom (LangGraph) | Multi-Agent |
|----------|---------------|----------------|-------------------|-------------|
| **Development Effort** | Low | Medium | High | Very High |
| **Flexibility** | Limited | Medium | High | Very High |
| **True Agentic** | Yes | No (fixed flow) | Yes | Yes |
| **Debugging** | Hard | Good | Medium | Hard |
| **Observability** | Limited | Excellent | Good | Medium |
| **Scalability** | Auto | Auto | Manual | Manual |
| **Cost Control** | Medium | Good | Good | Medium |
| **AWS Native** | ✅✅✅ | ✅✅✅ | ✅✅ | ✅✅ |
| **Accuracy Potential** | 90-93% | 88-92% | 93-96% | 94-97% |
| **Production Readiness** | High | High | Medium | Medium |

---

## Recommendation for Payroll Rules Generation

### Primary Recommendation: **Option 3 (Custom LangGraph on ECS)**

**Rationale:**
1. **Accuracy requirement (95%+)** needs true agentic behavior with self-correction
2. **Complex domain** benefits from flexible tool use and dynamic planning
3. **AWS native** via Bedrock for LLM, Neptune for graph, OpenSearch for vectors
4. **Observability** achievable with LangSmith + CloudWatch
5. **Team control** over agent logic is important for payroll compliance

### Alternative: **Option 1 (Bedrock Agents)** if:
- Team wants faster time-to-market
- Lower operational overhead is priority
- Willing to accept 90-93% accuracy with more human review

### Consider **Option 4 (Multi-Agent)** if:
- Processing many countries in parallel
- Want clear separation between research/extraction/generation
- Have resources for complex infrastructure

---

## Implementation Roadmap

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        IMPLEMENTATION PHASES                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  PHASE 1 (Weeks 1-3): Foundation                                           │
│  ─────────────────────────────────                                         │
│  • Set up Neptune graph database with regulatory schema                    │
│  • Set up OpenSearch for document vectors                                  │
│  • Implement basic tools (search, query_graph)                            │
│  • Create evaluation dataset (test cases)                                 │
│                                                                             │
│  PHASE 2 (Weeks 4-6): Basic Agent                                          │
│  ────────────────────────────────                                          │
│  • Implement LangGraph agent structure                                     │
│  • Basic supervisor + search + extract + generate nodes                   │
│  • Deploy on ECS Fargate                                                  │
│  • Measure baseline accuracy                                              │
│                                                                             │
│  PHASE 3 (Weeks 7-9): Validation & Refinement                              │
│  ─────────────────────────────────────────────                             │
│  • Add validation node with test case execution                           │
│  • Implement retry logic                                                  │
│  • Add source tracing                                                     │
│  • Iterate on prompts for accuracy                                        │
│                                                                             │
│  PHASE 4 (Weeks 10-12): Production Hardening                               │
│  ───────────────────────────────────────────                               │
│  • Add comprehensive logging/monitoring                                   │
│  • Implement human review interface                                       │
│  • Load testing and optimization                                          │
│  • Documentation and runbooks                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

*Document created: January 2026*

