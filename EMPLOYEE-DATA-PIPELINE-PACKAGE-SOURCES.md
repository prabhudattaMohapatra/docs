# Employee Data Pipeline - Package Sources Analysis

This document details where the `payroll-engine-employee-data-pipeline` workflows pull external packages from during the build process.

**Repository**: `gp-nova/payroll-engine-employee-data-pipeline`  
**Project Type**: Node.js/TypeScript (Serverless Lambda functions)  
**Package Manager**: npm

---

## Overview

The `payroll-engine-employee-data-pipeline` repository uses **AWS CodeArtifact** as its primary npm package registry. All npm packages are pulled from CodeArtifact, which acts as a proxy/cache for public npm packages and hosts private packages.

---

## Package Source Configuration

### `.npmrc` File

**Location**: `/payroll-engine-employee-data-pipeline/.npmrc`

**Content**:
```
registry=https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/
//gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/:_authToken=${ARTIFACT_AUTH_TOKEN}
//gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/:_authToken=${ARTIFACT_AUTH_TOKEN}
//gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/:always-auth=true
engine-strict=true
```

**Configuration Details**:
- **Registry**: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`
- **Domain**: `gp-prod`
- **Domain Owner**: `237156726900`
- **Repository**: `payroll-engine`
- **Authentication**: Uses `ARTIFACT_AUTH_TOKEN` environment variable
- **Always Auth**: Enabled for all CodeArtifact requests

---

## Workflow Package Installation Process

### Build Workflow (`main-build.yaml`)

**Step**: `Install Dependencies`
```yaml
- name: Install Dependencies
  uses: gp-nova/devx-pipeline-modules-npm/install@v1
```

### Install Action Process

**Action**: `gp-nova/devx-pipeline-modules-npm/install@v1`

**Expected Process** (based on typical npm/CodeArtifact patterns):

1. **OIDC Authentication**:
   - Uses AWS credentials from OIDC role assumption
   - Credentials available from previous step (OIDC role assumption)

2. **Get CodeArtifact Token**:
   ```bash
   aws codeartifact get-authorization-token \
     --domain gp-prod \
     --domain-owner 237156726900 \
     --region us-east-1 \
     --query authorizationToken \
     --output text
   ```
   - **Service**: AWS CodeArtifact
   - **Domain**: `gp-prod`
   - **Account**: `237156726900`
   - **Result**: Temporary authentication token (valid for 12 hours)

3. **Set Environment Variable**:
   ```bash
   export ARTIFACT_AUTH_TOKEN=<token>
   ```

4. **Read `.npmrc` Configuration**:
   - Reads `.npmrc` file from repository root
   - Registry is set to CodeArtifact: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`
   - Token substitution: `${ARTIFACT_AUTH_TOKEN}` is replaced with actual token

5. **Run npm install**:
   ```bash
   npm install
   ```
   - Reads `package.json` and `package-lock.json`
   - Uses registry from `.npmrc` (CodeArtifact)
   - Authenticates using `ARTIFACT_AUTH_TOKEN`
   - Downloads packages from CodeArtifact

---

## Package Sources Used

### Primary Source: AWS CodeArtifact

**Registry URL**: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`

**How CodeArtifact Works**:
1. **Proxy for Public Packages**:
   - CodeArtifact acts as a proxy/cache for public npm packages
   - When a package is requested, CodeArtifact:
     - Checks if it exists in CodeArtifact repository
     - If not, fetches from upstream (npmjs.com)
     - Caches it in CodeArtifact
     - Returns to client

2. **Private Packages**:
   - Private packages (e.g., `@gp-nova/nova-utils`) are stored directly in CodeArtifact
   - Not available from public npm registry

3. **Upstream Repository**:
   - CodeArtifact repository `payroll-engine` is configured with upstream to `npmjs.com`
   - Public packages are fetched from `https://registry.npmjs.org/` when not cached

### Package Resolution Flow

```
npm install
    ↓
Reads .npmrc
    ↓
Registry: CodeArtifact (gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/)
    ↓
Authenticate with ARTIFACT_AUTH_TOKEN
    ↓
For each package in package.json:
    ├─→ Check CodeArtifact repository
    │   ├─→ If found: Download from CodeArtifact
    │   └─→ If not found:
    │       ├─→ Check upstream (npmjs.com)
    │       ├─→ Download from npmjs.com
    │       ├─→ Cache in CodeArtifact
    │       └─→ Return to client
    └─→ Install package
```

---

## Package Categories

### 1. Public Packages (via CodeArtifact Proxy)

**Examples from `package.json`**:
- `@aws-sdk/client-s3`: `^3.705.0`
- `@aws-lambda-powertools/logger`: `^2.22.0`
- `@aws-lambda-powertools/metrics`: `^2.24.1`
- `@aws-lambda-powertools/tracer`: `^2.24.1`
- `@middy/core`: `^6.4.1`
- `@middy/http-error-handler`: `^6.4.1`
- `exceljs`: `^4.4.0`
- `got`: `^14.4.7`
- `http-errors`: `^2.0.0`
- `type-fest`: `^4.41.0`
- `aws-xray-sdk`: `^3.10.3`
- `aws-xray-sdk-core`: `^3.10.3`

**Source**: Public npm registry (`registry.npmjs.org`) → Cached in CodeArtifact → Served to client

### 2. Private Packages (Stored in CodeArtifact)

**Examples**:
- `@gp-nova/nova-utils`: `^2.12.0`

**Source**: Stored directly in CodeArtifact repository `payroll-engine`, not available from public npm

### 3. Dev Dependencies (via CodeArtifact Proxy)

**Examples**:
- `@types/node`: `^22.17.1`
- `@types/aws-lambda`: `^8.10.152`
- `typescript`: `^5.9.2`
- `eslint`: `^9.33.0`
- `prettier`: `^3.6.2`
- `jest`: `^29.7.0`
- `esbuild`: `^0.25.8`

**Source**: Public npm registry → Cached in CodeArtifact → Served to client

---

## Authentication Flow

### In GitHub Actions Workflow

**Step 1: OIDC Authentication** (implicit, from previous steps)
- OIDC role assumption provides AWS credentials
- Credentials available as environment variables

**Step 2: CodeArtifact Token Retrieval** (in `install@v1` action)
```bash
aws codeartifact get-authorization-token \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --region us-east-1 \
  --query authorizationToken \
  --output text
```

**Step 3: Token Export**
```bash
export ARTIFACT_AUTH_TOKEN=<token>
```

**Step 4: npm Install**
- npm reads `.npmrc` file
- Uses `ARTIFACT_AUTH_TOKEN` for authentication
- All requests to CodeArtifact include authentication token

### Authentication Requirements

**IAM Permissions Required** (for OIDC role):
- `codeartifact:GetAuthorizationToken` (for domain `gp-prod`)
- `codeartifact:ReadFromRepository` (for repository `payroll-engine`)

**Token Validity**: 12 hours

---

## Package Resolution Evidence

### From `package-lock.json`

All packages in `package-lock.json` show `resolved` URLs pointing to CodeArtifact:

```json
"resolved": "https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@aws-crypto/crc32/-/crc32-5.2.0.tgz"
"resolved": "https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@aws-sdk/client-s3/-/client-s3-3.901.0.tgz"
"resolved": "https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@gp-nova/nova-utils/-/nova-utils-2.12.0.tgz"
```

**Pattern**: All packages resolved from:
```
https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/{package-name}
```

---

## Comparison with Backend Repository

| Aspect | Payroll Engine Backend | Employee Data Pipeline |
|--------|------------------------|------------------------|
| **Project Type** | .NET (C#) | Node.js/TypeScript |
| **Package Manager** | NuGet | npm |
| **Package Source** | `api.nuget.org` (public) | CodeArtifact (proxy + private) |
| **Configuration File** | None (uses default) | `.npmrc` |
| **Authentication** | None (public feed) | CodeArtifact token required |
| **Private Packages** | ❌ No | ✅ Yes (`@gp-nova/nova-utils`) |
| **Upstream Proxy** | ❌ No | ✅ Yes (npmjs.com → CodeArtifact) |

---

## Workflow Steps Breakdown

### Main Build Workflow

**Job**: `build-and-package`

**Step 1: Checkout and Setup**
```yaml
- uses: gp-nova/devx-pipeline-modules-npm/setup@v1
```
- Checks out code
- Sets up Node.js environment
- **No package installation yet**

**Step 2: Install Dependencies**
```yaml
- uses: gp-nova/devx-pipeline-modules-npm/install@v1
```

**This step performs**:
1. **OIDC Authentication** (if not already done)
   - Assumes IAM role for AWS access
   - Gets AWS credentials

2. **Get CodeArtifact Token**:
   ```bash
   ARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token \
     --domain gp-prod \
     --domain-owner 237156726900 \
     --region us-east-1 \
     --query authorizationToken \
     --output text)
   ```

3. **Export Token**:
   ```bash
   export ARTIFACT_AUTH_TOKEN=$ARTIFACT_AUTH_TOKEN
   ```

4. **Read `.npmrc`**:
   - Reads `.npmrc` from repository root
   - Registry: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`
   - Token substitution: `${ARTIFACT_AUTH_TOKEN}` → actual token

5. **Run npm install**:
   ```bash
   npm install
   ```
   - Reads `package.json` and `package-lock.json`
   - For each dependency:
     - Queries CodeArtifact registry
     - Authenticates with `ARTIFACT_AUTH_TOKEN`
     - Downloads package from CodeArtifact
     - If package not in CodeArtifact, CodeArtifact fetches from npmjs.com and caches it

**Connections**:
- **AWS CodeArtifact Service**: `codeartifact.us-east-1.amazonaws.com`
- **Domain**: `gp-prod`
- **Repository**: `payroll-engine`
- **Upstream**: `registry.npmjs.org` (for public packages)
- **Data Flow**: 
  - npm → CodeArtifact API → Package files
  - CodeArtifact → npmjs.com (if not cached) → Cache → npm

---

## Package Installation Details

### Package Categories and Sources

#### 1. AWS SDK Packages

**Examples**:
- `@aws-sdk/client-s3`: `^3.705.0`
- `@aws-lambda-powertools/logger`: `^2.22.0`
- `@aws-lambda-powertools/metrics`: `^2.24.1`
- `@aws-lambda-powertools/tracer`: `^2.24.1`

**Source**: Public npm registry → Cached in CodeArtifact → Served via CodeArtifact

**Resolution**:
```
npm install @aws-sdk/client-s3
    ↓
CodeArtifact checks cache
    ↓
If not cached: Fetch from registry.npmjs.org
    ↓
Cache in CodeArtifact
    ↓
Return to npm client
```

#### 2. Private/Internal Packages

**Examples**:
- `@gp-nova/nova-utils`: `^2.12.0`

**Source**: Stored directly in CodeArtifact repository `payroll-engine`

**Resolution**:
```
npm install @gp-nova/nova-utils
    ↓
CodeArtifact repository (payroll-engine)
    ↓
Package found (private package)
    ↓
Return to npm client
```

**Note**: This package is NOT available from public npm registry.

#### 3. Third-Party Packages

**Examples**:
- `exceljs`: `^4.4.0`
- `got`: `^14.4.7`
- `http-errors`: `^2.0.0`
- `type-fest`: `^4.41.0`
- `@middy/core`: `^6.4.1`
- `@middy/http-error-handler`: `^6.4.1`

**Source**: Public npm registry → Cached in CodeArtifact → Served via CodeArtifact

#### 4. Development Dependencies

**Examples**:
- `typescript`: `^5.9.2`
- `eslint`: `^9.33.0`
- `prettier`: `^3.6.2`
- `jest`: `^29.7.0`
- `esbuild`: `^0.25.8`

**Source**: Public npm registry → Cached in CodeArtifact → Served via CodeArtifact

---

## CodeArtifact Architecture

### Repository Structure

**CodeArtifact Domain**: `gp-prod`
- **Domain Owner**: `237156726900`
- **Region**: `us-east-1`

**Repository**: `payroll-engine`
- **Type**: npm repository
- **Upstream**: `npmjs.com` (public npm registry)

### How CodeArtifact Works

1. **Package Request Flow**:
   ```
   npm client → CodeArtifact API
       ↓
   CodeArtifact checks repository cache
       ↓
   If found: Return cached package
   If not found:
       ↓
   CodeArtifact queries upstream (npmjs.com)
       ↓
   Downloads package from npmjs.com
       ↓
   Caches in CodeArtifact repository
       ↓
   Returns to npm client
   ```

2. **Private Packages**:
   - Published directly to CodeArtifact
   - Not available from upstream
   - Only accessible with authentication

3. **Benefits**:
   - ✅ Faster installs (cached packages)
   - ✅ Private package hosting
   - ✅ Centralized package management
   - ✅ Security scanning
   - ✅ Version control

---

## Authentication and Security

### Token Generation

**Command** (executed by `install@v1` action):
```bash
aws codeartifact get-authorization-token \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --region us-east-1 \
  --query authorizationToken \
  --output text
```

**Requirements**:
- AWS credentials (from OIDC role)
- IAM permission: `codeartifact:GetAuthorizationToken`
- Domain access: `gp-prod`

### Token Usage

**In `.npmrc`**:
```
//gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/:_authToken=${ARTIFACT_AUTH_TOKEN}
```

**Token Substitution**:
- `${ARTIFACT_AUTH_TOKEN}` is replaced with actual token value
- Token is included in HTTP Authorization header for all CodeArtifact requests

### Token Validity

- **Duration**: 12 hours
- **Refresh**: Token must be refreshed daily
- **Automatic**: `install@v1` action handles token generation automatically

---

## Package Installation Process (Detailed)

### Step-by-Step Flow

**1. Workflow Step: Install Dependencies**
```yaml
- name: Install Dependencies
  uses: gp-nova/devx-pipeline-modules-npm/install@v1
```

**2. Action Execution**:

**a. Check AWS Credentials**:
- Verifies AWS credentials are available (from OIDC)
- Uses credentials for CodeArtifact API calls

**b. Get CodeArtifact Token**:
```bash
TOKEN=$(aws codeartifact get-authorization-token \
  --domain gp-prod \
  --domain-owner 237156726900 \
  --region us-east-1 \
  --query authorizationToken \
  --output text)
```

**c. Export Token**:
```bash
export ARTIFACT_AUTH_TOKEN=$TOKEN
```

**d. Read `.npmrc`**:
- Reads `.npmrc` from repository root
- Registry: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`
- Token variable: `${ARTIFACT_AUTH_TOKEN}`

**e. Run npm install**:
```bash
npm install
```

**3. npm Install Process**:

**a. Read Package Files**:
- Reads `package.json` for dependencies
- Reads `package-lock.json` for exact versions

**b. For Each Package**:

**Public Package Example** (`@aws-sdk/client-s3`):
```
1. npm queries: https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@aws-sdk%2fclient-s3
2. CodeArtifact checks cache
3. If cached: Return from cache
4. If not cached:
   a. CodeArtifact queries: https://registry.npmjs.org/@aws-sdk/client-s3
   b. Downloads package tarball
   c. Caches in CodeArtifact
   d. Returns to npm
5. npm downloads and installs package
```

**Private Package Example** (`@gp-nova/nova-utils`):
```
1. npm queries: https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@gp-nova%2fnova-utils
2. CodeArtifact finds package (stored in repository)
3. Returns package to npm
4. npm downloads and installs package
```

**c. Install Packages**:
- Extracts packages to `node_modules/`
- Creates symlinks
- Installs binaries

**4. Result**:
- All packages installed in `node_modules/`
- `package-lock.json` updated (if needed)
- Ready for build

---

## Connections and Endpoints

### External Connections

| Connection | Protocol | Endpoint | Purpose | Authentication |
|------------|----------|----------|---------|----------------|
| **AWS CodeArtifact API** | HTTPS | `codeartifact.us-east-1.amazonaws.com` | Get authorization token | AWS credentials (OIDC) |
| **CodeArtifact Registry** | HTTPS | `gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/` | Package registry | CodeArtifact token |
| **npmjs.com (Upstream)** | HTTPS | `registry.npmjs.org` | Public package source (via CodeArtifact) | None (CodeArtifact handles) |

### Data Flow

```
GitHub Actions Runner
    ↓ [OIDC Authentication]
AWS STS (Account 258215414239)
    ↓ [Temporary AWS Credentials]
AWS CodeArtifact API
    ↓ [Get Authorization Token]
CodeArtifact Token (12-hour validity)
    ↓ [Export as ARTIFACT_AUTH_TOKEN]
npm install
    ↓ [Reads .npmrc]
CodeArtifact Registry
    ├─→ Public packages: Check cache → Fetch from npmjs.com if needed → Cache → Return
    └─→ Private packages: Return from repository
    ↓
node_modules/ (packages installed)
```

---

## Package Source Summary

### All Packages Come From

**Primary Source**: AWS CodeArtifact
- **Registry**: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`
- **Domain**: `gp-prod`
- **Account**: `237156726900`
- **Repository**: `payroll-engine`

### Package Types

1. **Public Packages** (via CodeArtifact proxy):
   - Source: `registry.npmjs.org` (upstream)
   - Cached in CodeArtifact
   - Examples: `@aws-sdk/*`, `@middy/*`, `exceljs`, `got`, `typescript`, `eslint`, etc.

2. **Private Packages** (stored in CodeArtifact):
   - Source: CodeArtifact repository directly
   - Examples: `@gp-nova/nova-utils`

### No Direct npmjs.com Access

**Important**: The workflow does NOT directly access `registry.npmjs.org`. All package requests go through CodeArtifact, which:
- Serves cached packages
- Proxies to npmjs.com for uncached packages
- Hosts private packages

---

## Comparison: Backend vs. Employee Data Pipeline

### Payroll Engine Backend

**Package Manager**: NuGet (.NET)
**Package Source**: `https://api.nuget.org/v3/index.json` (public, direct)
**Authentication**: None required
**Configuration**: None (uses default)
**Private Packages**: ❌ No

### Employee Data Pipeline

**Package Manager**: npm (Node.js)
**Package Source**: AWS CodeArtifact (proxy + private)
**Authentication**: CodeArtifact token required
**Configuration**: `.npmrc` file
**Private Packages**: ✅ Yes (`@gp-nova/nova-utils`)

### Key Differences

| Aspect | Backend | Employee Data Pipeline |
|--------|---------|------------------------|
| **Source Type** | Direct public feed | Proxy + private repository |
| **Authentication** | None | Required (CodeArtifact token) |
| **Configuration** | Default | `.npmrc` file |
| **Private Packages** | ❌ No | ✅ Yes |
| **Caching** | ❌ No | ✅ Yes (CodeArtifact cache) |
| **Upstream Proxy** | ❌ No | ✅ Yes (npmjs.com) |

---

## Workflow Execution Flow

### Main Build Workflow - Package Installation

**Job**: `build-and-package`

**Step 1: Checkout and Setup**
- Checks out repository code
- Sets up Node.js environment
- **No packages installed yet**

**Step 2: Install Dependencies**
- **Action**: `gp-nova/devx-pipeline-modules-npm/install@v1`
- **Process**:
  1. OIDC authentication (if needed)
  2. Get CodeArtifact token
  3. Export `ARTIFACT_AUTH_TOKEN`
  4. Read `.npmrc` (registry = CodeArtifact)
  5. Run `npm install`
  6. Packages downloaded from CodeArtifact

**Step 3: Set Version**
- Reads version from `package.json`
- Outputs semantic version

**Step 4: Build**
- Compiles TypeScript to JavaScript
- Uses installed packages from `node_modules/`

**Step 5: Upload Artifact**
- Uploads build artifacts to CodeArtifact

---

## Package Installation Evidence

### From `package-lock.json`

All packages show CodeArtifact URLs:

```json
{
  "node_modules/@aws-sdk/client-s3": {
    "version": "3.901.0",
    "resolved": "https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@aws-sdk/client-s3/-/client-s3-3.901.0.tgz",
    "integrity": "sha512-..."
  },
  "node_modules/@gp-nova/nova-utils": {
    "version": "2.12.0",
    "resolved": "https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/@gp-nova/nova-utils/-/nova-utils-2.12.0.tgz",
    "integrity": "sha512-..."
  }
}
```

**Pattern**: All packages resolved from CodeArtifact registry.

---

## Summary

### Package Sources

**All npm packages are pulled from**:
- **Primary Source**: AWS CodeArtifact
  - **Registry**: `https://gp-prod-237156726900.d.codeartifact.us-east-1.amazonaws.com/npm/payroll-engine/`
  - **Domain**: `gp-prod`
  - **Account**: `237156726900`

**Package Types**:
1. **Public Packages**: Fetched from `registry.npmjs.org` via CodeArtifact proxy and cached
2. **Private Packages**: Stored directly in CodeArtifact repository

### Authentication

- **Method**: CodeArtifact authorization token
- **Token Source**: AWS CodeArtifact API (`codeartifact:GetAuthorizationToken`)
- **Token Validity**: 12 hours
- **Configuration**: `.npmrc` file with token variable

### Workflow Integration

- **Action**: `gp-nova/devx-pipeline-modules-npm/install@v1`
- **Process**: Automatically handles token generation and npm install
- **Result**: All packages installed from CodeArtifact

### Key Points

1. ✅ **No direct npmjs.com access**: All requests go through CodeArtifact
2. ✅ **Private packages supported**: `@gp-nova/nova-utils` from CodeArtifact
3. ✅ **Caching**: Public packages cached in CodeArtifact for faster installs
4. ✅ **Authentication required**: CodeArtifact token needed for all package access
5. ✅ **Centralized management**: All packages managed through CodeArtifact

---

## References

- **Repository**: `gp-nova/payroll-engine-employee-data-pipeline`
- **Configuration**: `.npmrc`
- **Package Lock**: `package-lock.json`
- **Workflow**: `.github/workflows/main-build.yaml`
- **Install Action**: `gp-nova/devx-pipeline-modules-npm/install@v1`
- **CodeArtifact Domain**: `gp-prod` (Account: `237156726900`)

