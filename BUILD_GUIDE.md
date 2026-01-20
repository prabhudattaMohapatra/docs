# PayrollEngine Package Build Guide

Complete guide for building all PayrollEngine NuGet packages from source repositories.

---

## Prerequisites

### Required Software
- **.NET SDK 9.0** or later
  - Download: https://dotnet.microsoft.com/download
  - Verify: `dotnet --version` (should show 9.0.x or later)
- **Git** (for cloning repositories)
- **Visual Studio 2022** or **VS Code** (optional, for IDE support)

### Required Knowledge
- Basic understanding of .NET projects and NuGet packages
- Command line usage (PowerShell, Bash, or CMD)

---

## Build Process Overview

The PayrollEngine uses a standard .NET build process:

1. **Restore** dependencies (`dotnet restore`)
2. **Build** projects (`dotnet build`)
3. **Pack** NuGet packages (`dotnet pack`)
4. **Publish** to local feed (optional)

---

## Build Order (Critical)

Packages must be built in dependency order:

```
1. PayrollEngine.Core
   ↓
2. PayrollEngine.Client.Core
   ↓
3. PayrollEngine.Client.Scripting
   ↓
4. PayrollEngine.Client.Services
   ↓
5. PayrollEngine.Serilog
   ↓
6. PayrollEngine.Document
   ↓
7. Applications (Backend, Console, WebApp)
```

---

## Step-by-Step Build Instructions

### Step 1: Clone All Repositories

See `REQUIRED_REPOSITORIES.md` for the complete list. Clone all repositories to a common directory:

```bash
mkdir payroll-engine-build
cd payroll-engine-build

# Clone all repositories (use the script from REQUIRED_REPOSITORIES.md)
```

---

### Step 2: Configure Local NuGet Feed (Optional but Recommended)

Create a local NuGet feed to store built packages:

#### Option A: Local Folder Feed (Simplest)

```bash
# Create local feed directory
mkdir -p ~/local-nuget-feed

# Create nuget.config in your workspace root
cat > nuget.config << EOF
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="local" value="~/local-nuget-feed" />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
  </packageSources>
</configuration>
EOF
```

#### Option B: NuGet.Server (For Team Use)

Set up a NuGet.Server instance for shared access.

---

### Step 3: Build Each Package

For each repository, follow these steps:

#### Generic Build Commands

```bash
# Navigate to repository
cd PayrollEngine.Core

# Restore dependencies
dotnet restore

# Build solution/project
dotnet build -c Release

# Pack NuGet package
dotnet pack -c Release --no-build --output ./nupkg

# (Optional) Copy to local feed
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

---

## Repository-Specific Build Instructions

### 1. PayrollEngine.Core

**Location**: `PayrollEngine.Core/`  
**Solution**: `PayrollEngine.Core.sln`  
**Project**: Find the main `.csproj` file (usually in a subdirectory)

```bash
cd PayrollEngine.Core

# Find the main project
# Usually: Core/PayrollEngine.Core.csproj or similar

# Build
dotnet build PayrollEngine.Core.sln -c Release

# Pack
dotnet pack PayrollEngine.Core.sln -c Release --no-build --output ./nupkg
```

**Output**: `PayrollEngine.Core.0.9.0-beta.12.nupkg`

---

### 2. PayrollEngine.Client.Core

**Location**: `PayrollEngine.Client.Core/`  
**Solution**: `PayrollEngine.Client.Core.sln`  
**Special Notes**: 
- May require JSON schema generation
- Depends on PayrollEngine.Core

```bash
cd PayrollEngine.Client.Core

# Ensure PayrollEngine.Core is available (built or in local feed)
# Update PackageReference if using local feed

# Build
dotnet build PayrollEngine.Client.Core.sln -c Release

# Pack
dotnet pack PayrollEngine.Client.Core.sln -c Release --no-build --output ./nupkg
```

**Output**: `PayrollEngine.Client.Core.0.9.0-beta.12.nupkg`

**JSON Schema Generation** (if required):
```bash
# If JsonSchemaBuilder is needed, build it first:
cd ../PayrollEngine.JsonSchemaBuilder
dotnet build -c Release
dotnet publish -c Release

# Then run schema generation (if automated in build)
```

---

### 3. PayrollEngine.Client.Scripting

**Location**: `PayrollEngine.Client.Scripting/`  
**Solution**: `PayrollEngine.Client.Scripting.sln`  
**Dependencies**: PayrollEngine.Client.Core

```bash
cd PayrollEngine.Client.Scripting

# Build
dotnet build PayrollEngine.Client.Scripting.sln -c Release

# Pack
dotnet pack PayrollEngine.Client.Scripting.sln -c Release --no-build --output ./nupkg
```

**Output**: `PayrollEngine.Client.Scripting.0.9.0-beta.12.nupkg`

---

### 4. PayrollEngine.Client.Services

**Location**: `PayrollEngine.Client.Services/`  
**Solution**: `PayrollEngine.Client.Services.sln`  
**Dependencies**: PayrollEngine.Client.Core

```bash
cd PayrollEngine.Client.Services

# Build
dotnet build PayrollEngine.Client.Services.sln -c Release

# Pack
dotnet pack PayrollEngine.Client.Services.sln -c Release --no-build --output ./nupkg
```

**Output**: `PayrollEngine.Client.Services.0.9.0-beta.12.nupkg`

---

### 5. PayrollEngine.Serilog

**Location**: `PayrollEngine.Serilog/`  
**Solution**: `PayrollEngine.Serilog.sln`  
**Dependencies**: None (standalone)

```bash
cd PayrollEngine.Serilog

# Build
dotnet build PayrollEngine.Serilog.sln -c Release

# Pack
dotnet pack PayrollEngine.Serilog.sln -c Release --no-build --output ./nupkg
```

**Output**: `PayrollEngine.Serilog.0.9.0-beta.12.nupkg`

---

### 6. PayrollEngine.Document

**Location**: `PayrollEngine.Document/`  
**Solution**: `PayrollEngine.Document.sln`  
**Dependencies**: None (standalone)

```bash
cd PayrollEngine.Document

# Build
dotnet build PayrollEngine.Document.sln -c Release

# Pack
dotnet pack PayrollEngine.Document.sln -c Release --no-build --output ./nupkg
```

**Output**: `PayrollEngine.Document.0.9.0-beta.12.nupkg`

---

### 7. PayrollEngine.Backend (Application)

**Location**: `PayrollEngine.Backend/`  
**Solution**: `PayrollEngine.Backend.sln`  
**Dependencies**: All core libraries  
**Note**: This is an application, not a package (but may contain packages)

```bash
cd PayrollEngine.Backend

# Build (uses PackageReference, so packages must be available)
dotnet build PayrollEngine.Backend.sln -c Release

# If it contains packages, pack them:
dotnet pack PayrollEngine.Backend.sln -c Release --no-build --output ./nupkg
```

---

### 8. PayrollEngine.PayrollConsole (Application)

**Location**: `PayrollEngine.PayrollConsole/`  
**Solution**: `PayrollEngine.PayrollConsole.sln`  
**Dependencies**: Client.Services, Serilog, Document

```bash
cd PayrollEngine.PayrollConsole

# Build
dotnet build PayrollEngine.PayrollConsole.sln -c Release

# Publish (for deployment)
dotnet publish PayrollEngine.PayrollConsole.sln -c Release -o ./publish
```

---

### 9. PayrollEngine.WebApp (Application)

**Location**: `PayrollEngine.WebApp/`  
**Solution**: `PayrollEngine.WebApp.sln`  
**Dependencies**: Core, Client.Core, Serilog, Document

```bash
cd PayrollEngine.WebApp

# Build
dotnet build PayrollEngine.WebApp.sln -c Release

# Publish
dotnet publish PayrollEngine.WebApp.sln -c Release -o ./publish
```

---

## Automated Build Script

### PowerShell Script (Windows)

```powershell
# build-all.ps1
$ErrorActionPreference = "Stop"
$baseDir = "C:\path\to\payroll-engine-build"
$outputDir = "$baseDir\nupkg"
$localFeed = "$env:USERPROFILE\local-nuget-feed"

# Create output directory
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
New-Item -ItemType Directory -Force -Path $localFeed | Out-Null

# Build order
$repos = @(
    "PayrollEngine.Core",
    "PayrollEngine.Client.Core",
    "PayrollEngine.Client.Scripting",
    "PayrollEngine.Client.Services",
    "PayrollEngine.Serilog",
    "PayrollEngine.Document"
)

foreach ($repo in $repos) {
    Write-Host "Building $repo..." -ForegroundColor Green
    
    $repoPath = Join-Path $baseDir $repo
    $slnFile = Get-ChildItem -Path $repoPath -Filter "*.sln" | Select-Object -First 1
    
    if ($slnFile) {
        Push-Location $repoPath
        
        # Restore
        dotnet restore $slnFile.FullName
        
        # Build
        dotnet build $slnFile.FullName -c Release
        
        # Pack
        dotnet pack $slnFile.FullName -c Release --no-build --output $outputDir
        
        # Copy to local feed
        Copy-Item "$outputDir\*.nupkg" -Destination $localFeed -Force
        
        Pop-Location
        
        Write-Host "✅ $repo built successfully" -ForegroundColor Green
    } else {
        Write-Host "⚠️  No solution file found in $repo" -ForegroundColor Yellow
    }
}

Write-Host "`nAll packages built! Output: $outputDir" -ForegroundColor Cyan
Write-Host "Local feed: $localFeed" -ForegroundColor Cyan
```

### Bash Script (Linux/macOS)

```bash
#!/bin/bash
# build-all.sh

set -e

BASE_DIR="$HOME/payroll-engine-build"
OUTPUT_DIR="$BASE_DIR/nupkg"
LOCAL_FEED="$HOME/local-nuget-feed"

# Create directories
mkdir -p "$OUTPUT_DIR"
mkdir -p "$LOCAL_FEED"

# Build order
REPOS=(
    "PayrollEngine.Core"
    "PayrollEngine.Client.Core"
    "PayrollEngine.Client.Scripting"
    "PayrollEngine.Client.Services"
    "PayrollEngine.Serilog"
    "PayrollEngine.Document"
)

for repo in "${REPOS[@]}"; do
    echo "Building $repo..."
    
    REPO_PATH="$BASE_DIR/$repo"
    SLN_FILE=$(find "$REPO_PATH" -name "*.sln" -type f | head -n 1)
    
    if [ -n "$SLN_FILE" ]; then
        cd "$REPO_PATH"
        
        # Restore
        dotnet restore "$SLN_FILE"
        
        # Build
        dotnet build "$SLN_FILE" -c Release
        
        # Pack
        dotnet pack "$SLN_FILE" -c Release --no-build --output "$OUTPUT_DIR"
        
        # Copy to local feed
        cp "$OUTPUT_DIR"/*.nupkg "$LOCAL_FEED/" 2>/dev/null || true
        
        echo "✅ $repo built successfully"
    else
        echo "⚠️  No solution file found in $repo"
    fi
done

echo ""
echo "All packages built! Output: $OUTPUT_DIR"
echo "Local feed: $LOCAL_FEED"
```

---

## Handling Package References

### Problem: Projects Reference NuGet Packages

The projects use `<PackageReference>` pointing to NuGet.org. To use locally built packages:

### Solution 1: Use Local NuGet Feed (Recommended)

1. Build packages and copy to local feed
2. Configure `nuget.config` to include local feed first
3. Restore will use local packages

```xml
<!-- nuget.config -->
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="local" value="~/local-nuget-feed" />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
  </packageSources>
</configuration>
```

### Solution 2: Replace with Project References (For Development)

Modify `.csproj` files to use project references:

```xml
<!-- Change from: -->
<PackageReference Include="PayrollEngine.Core" Version="0.9.0-beta.12" />

<!-- To: -->
<ProjectReference Include="../../PayrollEngine.Core/Core/PayrollEngine.Core.csproj" />
```

**Note**: This requires all projects to be in the same solution or accessible via relative paths.

---

## Version Management

### Current Version
- **Version**: `0.9.0-beta.12`
- **Location**: `Directory.Build.props` (in each repository or solution root)

### Changing Version

To build with a different version:

1. **Option A**: Edit `Directory.Build.props`:
```xml
<Version>0.9.0-beta.13</Version>
```

2. **Option B**: Override at build time:
```bash
dotnet pack -c Release -p:Version=0.9.0-beta.13
```

---

## Troubleshooting

### Error: "Package not found"

**Problem**: Build fails because a dependency package is not found.

**Solution**:
1. Ensure dependencies are built in order
2. Add packages to local NuGet feed
3. Or use project references

### Error: "Version mismatch"

**Problem**: Package version doesn't match what's expected.

**Solution**:
1. Check `Directory.Build.props` for version
2. Ensure all packages use the same version
3. Or update PackageReference versions

### Error: "JSON Schema Builder not found"

**Problem**: `PayrollEngine.Client.Core` requires JsonSchemaBuilder.

**Solution**:
1. Build `PayrollEngine.JsonSchemaBuilder` first
2. Add to PATH or use full path
3. Or skip schema generation (if optional)

### Error: "Multiple solution files"

**Problem**: Repository has multiple `.sln` files.

**Solution**:
1. Identify the main solution (usually at root)
2. Or build specific projects directly

---

## Verification

### Verify Package Contents

```bash
# List package contents
dotnet nuget locals all --list

# Inspect package
nuget list -Source ~/local-nuget-feed

# Or use NuGet Package Explorer (GUI tool)
```

### Verify Build Success

After building, check:
- [ ] All `.nupkg` files created in output directory
- [ ] Package sizes are reasonable (not empty)
- [ ] No build errors or warnings
- [ ] Dependencies resolve correctly

---

## Quick Reference

### Build Single Package
```bash
cd <Repository>
dotnet restore
dotnet build -c Release
dotnet pack -c Release --no-build --output ./nupkg
```

### Build All Packages (Script)
```bash
# Windows
.\build-all.ps1

# Linux/macOS
./build-all.sh
```

### Publish to Local Feed
```bash
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

### Use Local Packages
```bash
# Configure nuget.config first
dotnet restore
```

---

## Next Steps

After building packages:

1. **Configure Local Feed**: Set up `nuget.config` to use local packages
2. **Build Applications**: Build Backend, Console, WebApp using local packages
3. **Test**: Run applications to verify everything works
4. **Deploy**: Use built packages for deployment

---

## Additional Resources

- [.NET CLI Documentation](https://docs.microsoft.com/dotnet/core/tools/)
- [NuGet Package Creation](https://docs.microsoft.com/nuget/create-packages/creating-a-package)
- [Local NuGet Feeds](https://docs.microsoft.com/nuget/hosting-packages/local-feeds)

---

*Document Generated: 2025-01-05*  
*PayrollEngine Package Build Guide*

