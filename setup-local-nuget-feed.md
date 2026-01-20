# Setup Local NuGet Feed and Publish PayrollEngine.Core

Complete guide to set up a local NuGet feed and publish PayrollEngine.Core package.

---

## Step 1: Create Local NuGet Feed Directory

```bash
# Create the feed directory
mkdir -p ~/local-nuget-feed

# Verify it was created
ls -la ~/local-nuget-feed
```

**Location**: `~/local-nuget-feed` (or `$HOME/local-nuget-feed`)

---

## Step 2: Configure NuGet to Use Local Feed

### Option A: Global NuGet Config (Recommended)

**⚠️ IMPORTANT: Source Order Matters!**

NuGet checks sources in the order they appear. **Local-feed MUST be first** to ensure PayrollEngine packages come from local feed, not nuget.org.

```bash
# Check current source order
dotnet nuget list source

# If nuget.org is first, remove and re-add sources in correct order
# Remove existing sources (if needed)
dotnet nuget remove source nuget.org 2>/dev/null || true
dotnet nuget remove source local-feed 2>/dev/null || true

# Add local-feed FIRST
dotnet nuget add source ~/local-nuget-feed --name local-feed

# Add nuget.org SECOND
dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Verify source order (local-feed should be first)
dotnet nuget list source
```

You should see:
```
Registered Sources:
  1.  local-feed [Enabled]      ← Must be FIRST
       /Users/pmohapatra/local-nuget-feed
  2.  nuget-org [Enabled]       ← Second
       https://api.nuget.org/v3/index.json
```

**Note**: The NuGet config file is located at `~/.nuget/NuGet/NuGet.Config`. The order in this file determines which source is checked first.

### Option B: Per-Project NuGet Config

Create `nuget.config` in your workspace root:

```bash
# Navigate to your workspace root
cd ~/repos/payroll/prabhu_aws

# Create nuget.config
cat > nuget.config << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="local-feed" value="~/local-nuget-feed" />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
  </packageSources>
  <packageSourceMapping>
    <packageSource key="local-feed">
      <package pattern="PayrollEngine.*" />
    </packageSource>
    <packageSource key="nuget.org">
      <package pattern="*" />
    </packageSource>
  </packageSourceMapping>
</configuration>
EOF
```

**Note**: The `packageSourceMapping` ensures PayrollEngine packages come from local feed, others from nuget.org.

---

## Step 3: Build and Publish PayrollEngine.Core

### 3.1 Navigate to PayrollEngine.Core

```bash
cd ~/repos/payroll/prabhu_aws/payroll-engine-core
```

### 3.2 Restore Dependencies

```bash
dotnet restore PayrollEngine.Core.sln
```

### 3.3 Build the Solution

```bash
dotnet build PayrollEngine.Core.sln -c Release
```

Expected output:
```
Build succeeded.
    0 Warning(s)
    0 Error(s)
```

### 3.4 Create NuGet Package

```bash
dotnet pack PayrollEngine.Core.sln -c Release --no-build --output ./nupkg
```

This creates: `nupkg/PayrollEngine.Core.0.9.0-beta.13.nupkg`

### 3.5 Publish to Local Feed

```bash
# Copy package to local feed
cp ./nupkg/*.nupkg ~/local-nuget-feed/

# Or use dotnet nuget push (alternative method)
dotnet nuget push ./nupkg/*.nupkg --source local-feed --skip-duplicate
```

### 3.6 Verify Package in Local Feed

```bash
# List packages in local feed
ls -lh ~/local-nuget-feed/

# Or use NuGet CLI
dotnet nuget list source
dotnet list package --source local-feed
```

You should see:
```
PayrollEngine.Core.0.9.0-beta.13.nupkg
```

### 3.7 Verify Local Feed is Being Used (After Restore)

**⚠️ Critical**: After restoring packages, verify they actually came from local feed:

```bash
# Method 1: Check .nupkg.metadata file (Most Reliable)
cat ~/.nuget/packages/payrollengine.core/0.9.0-beta.13/.nupkg.metadata | grep '"source"'
```

You should see:
```json
"source": "/Users/pmohapatra/local-nuget-feed"
```

If you see `"source": "https://api.nuget.org/v3/index.json"`, the package came from nuget.org instead.

**Quick verification command:**
```bash
# Check source for any PayrollEngine package
cat ~/.nuget/packages/payrollengine.core/0.9.0-beta.13/.nupkg.metadata | grep -o '"source": "[^"]*"'
```

**Expected output**: `"source": "/Users/pmohapatra/local-nuget-feed"`

---

## Step 4: Test Using the Local Package

### 4.1 Create a Test Project (Optional)

```bash
# Create test directory
mkdir -p ~/test-payroll-engine
cd ~/test-payroll-engine

# Create test project
dotnet new console -n TestPayrollEngine

# Add package reference
cd TestPayrollEngine
dotnet add package PayrollEngine.Core --source local-feed
```

### 4.2 Verify Package Restore

```bash
# Clear cache first to ensure fresh restore
dotnet nuget locals all --clear

# Restore
dotnet restore
```

### 4.3 Verify Local Feed Was Used

After restore, verify the package came from local feed:

```bash
# Check .nupkg.metadata file
cat ~/.nuget/packages/payrollengine.core/0.9.0-beta.13/.nupkg.metadata | grep '"source"'
```

Should show: `"source": "/Users/pmohapatra/local-nuget-feed"`

---

## Step 5: Quick Build Script

Create a script to automate the process:

```bash
#!/bin/bash
# build-and-publish-core.sh

set -e

FEED_DIR=~/local-nuget-feed
REPO_DIR=~/repos/payroll/prabhu_aws/payroll-engine-core

echo "📦 Building PayrollEngine.Core..."

cd $REPO_DIR

# Restore
echo "1️⃣  Restoring dependencies..."
dotnet restore PayrollEngine.Core.sln

# Build
echo "2️⃣  Building solution..."
dotnet build PayrollEngine.Core.sln -c Release

# Pack
echo "3️⃣  Creating NuGet package..."
dotnet pack PayrollEngine.Core.sln -c Release --no-build --output ./nupkg

# Publish to local feed
echo "4️⃣  Publishing to local feed..."
mkdir -p $FEED_DIR
cp ./nupkg/*.nupkg $FEED_DIR/

echo "✅ PayrollEngine.Core published to $FEED_DIR"
echo ""
echo "Package: $(ls $FEED_DIR/*.nupkg)"
```

Make it executable:
```bash
chmod +x build-and-publish-core.sh
./build-and-publish-core.sh
```

---

## Step 6: Using Environment Variable (Optional)

The `PayrollEngine.Core.csproj` has a built-in target that auto-publishes if `PayrollEnginePackageDir` is set:

```bash
# Set environment variable
export PayrollEnginePackageDir=~/local-nuget-feed

# Build and pack (auto-publishes)
cd ~/repos/payroll/prabhu_aws/payroll-engine-core
dotnet pack PayrollEngine.Core.sln -c Release

# Package is automatically copied to ~/local-nuget-feed
```

---

## Troubleshooting

### Issue: Package Not Found During Restore

**Solution**: Verify NuGet source is configured:
```bash
dotnet nuget list source
```

If local-feed is missing:
```bash
dotnet nuget add source ~/local-nuget-feed --name local-feed
```

### Issue: Packages Coming from nuget.org Instead of Local Feed

**Solution**: This happens when nuget.org is checked before local-feed. Fix source order:

```bash
# Clear cache first
dotnet nuget locals all --clear

# Remove and re-add sources in correct order
dotnet nuget remove source nuget.org 2>/dev/null || true
dotnet nuget remove source local-feed 2>/dev/null || true

# Add local-feed FIRST
dotnet nuget add source ~/local-nuget-feed --name local-feed

# Add nuget.org SECOND
dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Verify order
dotnet nuget list source

# Restore again
dotnet restore
```

Then verify using `.nupkg.metadata` file (see Step 3.7).

### Issue: Wrong Package Version

**Solution**: Check version in `Directory.Build.props`:
```bash
grep Version payroll-engine-core/Directory.Build.props
```

Update if needed, then rebuild.

### Issue: Package Already Exists

**Solution**: Use `--skip-duplicate` or remove old package:
```bash
rm ~/local-nuget-feed/PayrollEngine.Core.*.nupkg
# Then republish
```

### Issue: Path Not Found

**Solution**: Use absolute path:
```bash
# Instead of ~/local-nuget-feed, use:
export FEED_DIR=$HOME/local-nuget-feed
dotnet nuget add source $FEED_DIR --name local-feed
```

### Issue: How to Verify Which Source Was Used

**Solution**: Check the `.nupkg.metadata` file in the NuGet cache:

```bash
# For any PayrollEngine package
cat ~/.nuget/packages/payrollengine.core/0.9.0-beta.13/.nupkg.metadata | grep '"source"'
```

- If shows `"source": "/Users/pmohapatra/local-nuget-feed"` → ✅ Local feed was used
- If shows `"source": "https://api.nuget.org/v3/index.json"` → ❌ nuget.org was used

**Note**: This is the most reliable verification method. The metadata file is created during restore and records the actual source used.

---

## Verification Checklist

- [ ] Local feed directory created: `~/local-nuget-feed`
- [ ] NuGet source added: `dotnet nuget list source` shows `local-feed`
- [ ] **Source order verified**: `local-feed` is FIRST in the source list
- [ ] Package built: `nupkg/PayrollEngine.Core.0.9.0-beta.13.nupkg` exists
- [ ] Package published: `~/local-nuget-feed/PayrollEngine.Core.0.9.0-beta.13.nupkg` exists
- [ ] Cache cleared: `dotnet nuget locals all --clear` (before first restore)
- [ ] Restore successful: `dotnet restore` completes without errors
- [ ] **Verified source usage**: `.nupkg.metadata` shows `"source": "/Users/pmohapatra/local-nuget-feed"`

---

## Next Steps

After publishing PayrollEngine.Core, build other packages in dependency order:

1. ✅ PayrollEngine.Core (done)
2. PayrollEngine.Client.Core (depends on Core)
3. PayrollEngine.Client.Scripting (depends on Client.Core)
4. PayrollEngine.Client.Services (depends on Client.Core)
5. PayrollEngine.Serilog (standalone)
6. PayrollEngine.Document (standalone)

Then build applications (Backend, WebApp, Console) using the local feed.

**Important**: After building each package, verify it's in the local feed and verify source usage after restore using the `.nupkg.metadata` method (Step 3.7).

---

## Quick Reference: Verify Local Feed Usage

**After any restore, verify packages came from local feed:**

```bash
# Check source for PayrollEngine.Core
cat ~/.nuget/packages/payrollengine.core/0.9.0-beta.13/.nupkg.metadata | grep -o '"source": "[^"]*"'

# Check source for PayrollEngine.Client.Core
cat ~/.nuget/packages/payrollengine.client.core/0.9.0-beta.13/.nupkg.metadata | grep -o '"source": "[^"]*"'

# Check all PayrollEngine packages at once
for pkg in ~/.nuget/packages/payrollengine.*/0.9.0-beta.13/.nupkg.metadata; do
    if [ -f "$pkg" ]; then
        echo "$(dirname $(dirname $pkg)):"
        grep -o '"source": "[^"]*"' "$pkg"
        echo ""
    fi
done
```

**Expected**: All should show `"source": "/Users/pmohapatra/local-nuget-feed"`

---

*Document Generated: 2025-01-05*  
*Setup Local NuGet Feed Guide*

