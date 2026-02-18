# Copying Strategy: Payroll Engine Repositories

## Overview
This document outlines the strategy for copying content from upstream Payroll Engine repositories to gp-nova destination repositories without git history, build artifacts, and workflow files.

---

## Repository Mappings

| # | Source Repository | Destination Repository | Status |
|---|-------------------|------------------------|--------|
| 1 | `PayrollEngine.Client.Scripting` | `payroll-engine-client-scripting` | ✅ Completed |
| 2 | `PayrollEngine.Client.Services` | `payroll-engine-client-services` | 📋 Pending README update |
| 3 | `PayrollEngine.Document` | `payroll-engine-document` | 📋 Pending README update |
| 4 | `PayrollEngine.SqlServer.DbQuery` | `payroll-engine-dbQueryTool` | 📋 Pending README update |
| 5 | `PayrollEngine.Serilog` | `payroll-engine-serilog` | 📋 Pending README update |
| 6 | `PayrollEngine.JSONSchemaBuilder` | `payroll-engine-jsonSchemaBuilder` | 📋 Pending README update |

---

## 1. PayrollEngine.Client.Scripting → payroll-engine-client-scripting

**Status**: ✅ Completed

### Source Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/PayrollEngine.Client.Scripting`

### Destination Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-client-scripting`

### Content to Copy

| Include | Exclude |
|---------|---------|
| `Client.Scripting/` - Main source code | `.git/` - Git history |
| `Client.Scripting.Tests/` - Test projects | `.github/workflows/release.yml` - CI workflow |
| `Directory.Build.props` - Build configuration | `bin/`, `obj/` - Build outputs |
| `docfx/` - Documentation files | `.vs/` - Visual Studio cache |
| `LICENSE` - License file | `artifacts/` - Build artifacts |
| `PayrollEngine.Client.Scripting.sln` - Solution | `_site/` - DocFX generated site |
| `PayrollEngine.Client.Scripting.ndproj` - NDepend | `*.user`, `*.suo` - User files |
| `PayrollEngine.Client.Scripting.sln.DotSettings` | |

### Preserve in Destination
- `catalog-info.yaml`
- `docs/index.md`
- `mkdocs.yml`
- `.github/CODEOWNERS`

---

## 2. PayrollEngine.Client.Services → payroll-engine-client-services

**Status**: 📋 Content copied, README needs Project Information table, .gitignore needs merge

### Source Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/PayrollEngine.Client.Services`

### Destination Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-client-services`

### Content to Copy

| Include | Exclude |
|---------|---------|
| `Client.Services/` - Main source code | `.git/` - Git history |
| ├── `Scripting/` - Scripting classes | `.github/workflows/release.yml` - CI workflow |
| ├── `Scripting.Function.Api/` - Function controllers | `bin/`, `obj/` - Build outputs |
| └── `Scripting.Runtime.Api/` - Runtime classes | `.vs/` - Visual Studio cache |
| `Directory.Build.props` - Build configuration | `artifacts/` - Build artifacts |
| `docfx/` - Documentation files | `_site/` - DocFX generated site |
| `LICENSE` - License file | `*.user`, `*.suo` - User files |
| `PayrollEngine.Client.Services.sln` - Solution | |
| `PayrollEngine.Client.Services.ndproj` - NDepend | |
| `PayrollEngine.Client.Services.sln.DotSettings` | |

### Preserve in Destination
- `catalog-info.yaml` ✅ (exists)

### Post-Copy Actions Required
- [ ] Update README.md with Project Information table
- [ ] Merge .gitignore (currently only 3 lines - needs comprehensive VS patterns)

---

## 3. PayrollEngine.Document → payroll-engine-document

**Status**: 📋 Content copied, README needs Project Information table

### Source Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/PayrollEngine.Document`

### Destination Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-document`

### Content to Copy

| Include | Exclude |
|---------|---------|
| `Document/` - Main source code | `.git/` - Git history |
| ├── `CellExtensions.cs` | `.github/workflows/release.yml` - CI workflow |
| ├── `DataMerge.cs` | `bin/`, `obj/` - Build outputs |
| ├── `PDFSimpleExportExtensions.cs` | `.vs/` - Visual Studio cache |
| └── `WorkbookExtensions.cs` | `artifacts/` - Build artifacts |
| `Directory.Build.props` - Build configuration | `_site/` - DocFX generated site |
| `LICENSE` - License file | `*.user`, `*.suo` - User files |
| `PayrollEngine.Document.sln` - Solution | |
| `PayrollEngine.Document.ndproj` - NDepend | |
| `PayrollEngine.Document.sln.DotSettings` | |

### Preserve in Destination
- (No catalog-info.yaml exists)

### Post-Copy Actions Required
- [ ] Update README.md with Project Information table
- [x] .gitignore already has comprehensive VS patterns

---

## 4. PayrollEngine.SqlServer.DbQuery → payroll-engine-dbQueryTool

**Status**: 📋 Content copied, README needs Project Information table

### Source Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/PayrollEngine.SqlServer.DbQuery`

### Destination Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-dbQueryTool`

### Content to Copy

| Include | Exclude |
|---------|---------|
| `DbQuery/` - Main source code | `.git/` - Git history |
| ├── `Command.cs`, `CommandBase.cs` | `bin/`, `obj/` - Build outputs |
| ├── `QueryCommand.cs` | `.vs/` - Visual Studio cache |
| ├── `TestServerCommand.cs` | `artifacts/` - Build artifacts |
| ├── `TestSqlConnectionCommand.cs` | `*.user`, `*.suo` - User files |
| ├── `appsettings.json` | |
| └── `Properties/launchSettings.json` | |
| `Directory.Build.props` - Build configuration | |
| `LICENSE` - License file | |
| `PayrollEngine.SqlServer.DbQuery.sln` - Solution | |
| `PayrollEngine.SqlServer.DbQuery.ndproj` - NDepend | |

### Notes
- **No `.github/` folder in source** - nothing to exclude
- Source does not have `docfx/` documentation

### Preserve in Destination
- (No catalog-info.yaml exists)

### Post-Copy Actions Required
- [ ] Update README.md with Project Information table
- [x] .gitignore already has comprehensive VS patterns

---

## 5. PayrollEngine.Serilog → payroll-engine-serilog

**Status**: 📋 Content copied, README needs Project Information table

### Source Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/PayrollEngine.Serilog`

### Destination Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-serilog`

### Content to Copy

| Include | Exclude |
|---------|---------|
| `Serilog/` - Main source code | `.git/` - Git history |
| ├── `ConfigurationExtensions.cs` | `.github/workflows/release.yml` - CI workflow |
| ├── `PayrollLog.cs` | `bin/`, `obj/` - Build outputs |
| └── `PayrollEngine.Serilog.xml` | `.vs/` - Visual Studio cache |
| `Directory.Build.props` - Build configuration | `artifacts/` - Build artifacts |
| `LICENSE` - License file | `*.user`, `*.suo` - User files |
| `PayrollEngine.Serilog.sln` - Solution | |
| `PayrollEngine.Serilog.ndproj` - NDepend | |
| `PayrollEngine.Serilog.sln.DotSettings` | |

### Notes
- Small library with minimal files
- No `docfx/` documentation
- No test projects

### Preserve in Destination
- (No catalog-info.yaml exists)

### Post-Copy Actions Required
- [ ] Update README.md with Project Information table
- [x] .gitignore already has comprehensive VS patterns

---

## 6. PayrollEngine.JSONSchemaBuilder → payroll-engine-jsonSchemaBuilder

**Status**: 📋 Content copied, README needs Project Information table

### Source Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/PayrollEngine.JSONSchemaBuilder`

### Destination Path
`/Users/pmohapatra/repos/payroll/prabhu_aws/payroll-engine-jsonSchemaBuilder`

### Content to Copy

| Include | Exclude |
|---------|---------|
| `JsonSchemaBuilder/` - Main source code | `.git/` - Git history |
| ├── `CommandLineArguments.cs` | `bin/`, `obj/` - Build outputs |
| ├── `Program.cs` | `.vs/` - Visual Studio cache |
| └── `Properties/launchSettings.json` | `artifacts/` - Build artifacts |
| `Directory.Build.props` - Build configuration | `*.user`, `*.suo` - User files |
| `LICENSE` - License file | |
| `PayrollEngine.JsonSchemaBuilder.sln` - Solution | |
| `PayrollEngine.JsonSchemaBuilder.ndproj` - NDepend | |

### Notes
- **No `.github/` folder in source** - nothing to exclude
- Small console application
- No `docfx/` documentation
- No test projects

### Preserve in Destination
- (No catalog-info.yaml exists)

### Post-Copy Actions Required
- [ ] Update README.md with Project Information table
- [x] .gitignore already has comprehensive VS patterns

---

## Generic Copy Command

Use `rsync` with exclusion patterns for any repository:

```bash
cd /Users/pmohapatra/repos/payroll/prabhu_aws

rsync -av \
  --exclude='.git' \
  --exclude='.github' \
  --exclude='bin' \
  --exclude='obj' \
  --exclude='.vs' \
  --exclude='artifacts' \
  --exclude='_site' \
  --exclude='**/bin' \
  --exclude='**/obj' \
  --exclude='**/.vs' \
  --exclude='**/packages' \
  --exclude='*.user' \
  --exclude='*.suo' \
  --exclude='*.sln.docstates' \
  <SOURCE_DIR>/ \
  <DEST_DIR>/
```

---

## .gitignore Merge Strategy

### Source .gitignore Pattern
Comprehensive Visual Studio .gitignore (~346 lines) including:
- Build artifacts (`bin/`, `obj/`, `artifacts/`)
- IDE files (`.vs/`, `*.user`, `*.suo`)
- NuGet packages
- Test results
- Documentation output (`_site/`)

### Destination-Specific Patterns to Add
```
.DS_Store
.dccache
```

### Merge Command
After copying, ensure destination-specific patterns are appended:
```bash
# Check if patterns exist, if not append them
grep -q ".DS_Store" <DEST>/.gitignore || echo ".DS_Store" >> <DEST>/.gitignore
grep -q ".dccache" <DEST>/.gitignore || echo ".dccache" >> <DEST>/.gitignore
```

---

## README Update Template

All destination READMEs should include the Project Information table after the title:

```markdown
# <Project Title>

👉 This library/application is part of the [Payroll Engine](https://github.com/Payroll-Engine/PayrollEngine/wiki).

## Project Information

<table>
<tr><td>Value Stream</td><td>core-platform</td></tr>
<tr><td>Domain</td><td>global-payroll</td></tr>
<tr><td>Bounded Context</td><td>payroll-engine</td></tr>
<tr><td>Team</td><td>gp-nova/payblaze</td></tr>
</table>

## Description

<original content follows...>
```

---

## Summary Checklist

### For Each Repository Copy:

**Pre-Copy:**
- [ ] Identify source and destination paths
- [ ] Review source content structure
- [ ] Note any `.github/workflows/` to exclude

**Copy Operation:**
- [ ] Run rsync with exclusion patterns
- [ ] Exclude `.git/` directory
- [ ] Exclude `.github/` directory (if exists)
- [ ] Exclude all `bin/`, `obj/` directories
- [ ] Exclude `.vs/` directories
- [ ] Exclude `artifacts/`, `_site/` directories

**Post-Copy:**
- [ ] Preserve existing `catalog-info.yaml` (if exists)
- [ ] Preserve existing `docs/` and `mkdocs.yml` (if exists)
- [ ] Merge `.gitignore` (add `.DS_Store`, `.dccache` if missing)
- [ ] Update `README.md` with Project Information table
- [ ] Verify no build artifacts were copied
- [ ] Test build (`dotnet restore && dotnet build`)

---

## Current Status Summary

| Repository | Content Copied | .gitignore Merged | README Updated | catalog-info.yaml |
|------------|---------------|-------------------|----------------|-------------------|
| payroll-engine-client-scripting | ✅ | ✅ | ✅ | ✅ Preserved |
| payroll-engine-client-services | ✅ | ❌ Needs merge | ❌ Needs update | ✅ Exists |
| payroll-engine-document | ✅ | ✅ | ❌ Needs update | ❌ Missing |
| payroll-engine-dbQueryTool | ✅ | ✅ | ❌ Needs update | ❌ Missing |
| payroll-engine-serilog | ✅ | ✅ | ❌ Needs update | ❌ Missing |
| payroll-engine-jsonSchemaBuilder | ✅ | ✅ | ❌ Needs update | ❌ Missing |
