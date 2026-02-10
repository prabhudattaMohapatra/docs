# Diagram 5: Option 4 — Expression as data → AST → result

No code execution: only parsed expression and context. Closed DSL, no eval(arbitraryCode).

```mermaid
flowchart LR
    subgraph Storage["Storage"]
        DB[(DB: WageType.ValueExpression)]
    end

    subgraph Engine["Engine"]
        Parse[Parser]
        AST[AST]
        Eval[Evaluator]
        Context[Context: case values, collector, lookups, period, employee]
        Eval --> Context
    end

    subgraph Result["Output"]
        WT[WageTypeResult]
    end

    DB -->|"e.g. CaseValue('BaseSalary') * 0.2"| Parse
    Parse --> AST
    AST --> Eval
    Eval --> WT

    note1[No eval(arbitraryCode). Whitelist of functions only.]
```
