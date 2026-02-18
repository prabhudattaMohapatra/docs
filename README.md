# Docs index

Documentation is grouped by topic (one level of folders; max depth two levels including files).

| Folder | Contents |
|--------|----------|
| [**poc/**](poc/) | POC and beginner guides: precompiled JAR, payroll-engine-poc, payroll-regulations-poc, runtime rules evaluation |
| [**architecture/**](architecture/) | Payroll engine architecture, learning pathway, from-scratch decisions, core vs dynamic, language choice (Java/TypeScript) |
| [**execution-and-rules/**](execution-and-rules/) | Runtime rules, payrun processor, regulation ingestion, Rules.cs, Java rules implementation, France regulation |
| [**rfc/**](rfc/) | RFCs: format template, language choice, runtime rules scripting, core/dynamic separation |
| [**ai-rules-generation/**](ai-rules-generation/) | AI-powered payroll rules generation: architecture, agents, LLM vs agents, knowledge store |
| [**infrastructure/**](infrastructure/) | Exemplar container/ECS, migration to self-contained stack, pipeline, OIDC, deployment, promotion flow |
| [**build-and-packages/**](build-and-packages/) | Build guide, NuGet/local feed, CodeArtifact, Docker build, required repos, GitHub workflows |
| [**capabilities/**](capabilities/) | Console scripting, payrun backend, employee management capabilities |
| [**migration-analysis/**](migration-analysis/) | Java vs TypeScript migration: analysis, comparison matrix, benefits, negatives |
| [**testing/**](testing/) | Performance testing plan, database performance testing |
| [**payroll-flow/**](payroll-flow/) | Payroll flow diagram, calculation flow, salary conversion, field mapping, S3 paths |
| [**diagram/**](diagram/) | Execution model option diagrams (five-option flow comparison, option 1–4 details) |

Root: `docker-compose.yml` (local/compose config).

**Note:** Links between docs in the same folder work as before. Links from one folder to another may need path updates (e.g. `../rfc/rfc_format.md`) if you open files from a tool that resolves links relative to the repo root.
