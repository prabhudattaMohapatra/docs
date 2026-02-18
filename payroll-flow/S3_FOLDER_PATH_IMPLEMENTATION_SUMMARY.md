# S3 Folder Path Implementation Summary

## Overview
This document summarizes the changes made to support S3 folder paths as an alternative input method for PDF processing, in addition to the existing web URL-based approach. These changes enable the payroll engine to process PDFs directly from S3 folders instead of requiring web URLs.

## Branch Information
- **Branch**: NOVACORE-23001-2
- **Date**: Current unstaged changes
- **Purpose**: Add S3 folder path support for PDF downloads alongside web URLs

---

## Files Modified

### 1. `function/dsl_rule_extractor/app.py`
**Changes:**
- Updated logging level configuration to properly handle string-to-logging-level conversion
- Modified handler documentation to include both input methods:
  - `pdf_urls`: List of web URLs (existing)
  - `s3_folder_path`: S3 folder path (new)
- Updated logging messages to differentiate between S3 folder and URL-based processing

**Key Code Changes:**
```python
# Before: Only pdf_urls
# After: Supports both pdf_urls OR s3_folder_path

if request.s3_folder_path:
    logger.info(f"Starting extraction from S3 folder: {request.s3_folder_path}")
else:
    logger.info(f"Starting extraction for {len(request.pdf_urls)} PDF(s)")
```

---

### 2. `function/intermediate_dsl_generator/app.py`
**Changes:**
- Updated logging level configuration (same as dsl_rule_extractor)
- No functional changes related to S3 folder path (logging improvement only)

---

### 3. `module/models/extraction_request.py`
**Changes:**
- Added new optional field: `s3_folder_path: Optional[str] = None`
- Modified validation logic to accept either `pdf_urls` OR `s3_folder_path` (mutually exclusive)
- Updated `from_lambda_event()` method to handle both input types
- Modified output key generation to use S3 folder path hash when provided

**Key Validation Changes:**
```python
# Before: pdf_urls was required
# After: Either pdf_urls OR s3_folder_path must be provided

if not self.s3_folder_path and (not isinstance(self.pdf_urls, list) or not self.pdf_urls):
    raise ValueError("Either pdf_urls or s3_folder_path must be provided")
```

**Event Structure Support:**
```python
# Option 1: Web URLs (existing)
{
    "pdf_urls": ["https://example.com/payroll-document.pdf"],
    "country": "IN",
    "output_bucket": "my-bucket"
}

# Option 2: S3 Folder Path (new)
{
    "s3_folder_path": "payroll-engine-inputs/source-pdfs/IN/",
    "country": "IN",
    "output_bucket": "my-bucket"
}
```

---

### 4. `module/services/pdf_processing_service.py`
**Changes:**
- Added `boto3` import and S3 client initialization
- Added three new methods:
  1. `list_pdfs_from_s3_folder()` - Lists all PDF files in an S3 folder
  2. `download_from_s3()` - Downloads a single PDF from S3
  3. `download_and_extract_text_from_s3_folder()` - Main method that processes all PDFs from an S3 folder

**New Methods:**

#### `list_pdfs_from_s3_folder(s3_folder_path: str) -> List[str]`
- Parses S3 path (supports both `bucket/prefix` and `s3://bucket/prefix` formats)
- Uses S3 paginator to list all objects with `.pdf` extension
- Returns list of S3 keys in format `bucket/key`
- Raises error if no PDFs found

#### `download_from_s3(bucket: str, key: str) -> bytes`
- Downloads PDF content from S3
- Validates file size against `max_file_size` limit
- Verifies PDF content type/magic bytes
- Handles S3-specific errors (NoSuchKey, ClientError)

#### `download_and_extract_text_from_s3_folder(s3_folder_path: str) -> str`
- Orchestrates the full process:
  1. Lists all PDFs in the folder
  2. Downloads each PDF sequentially
  3. Extracts text from each PDF
  4. Combines all text with document separators
  5. Returns combined text content
- Includes error handling to continue processing if individual PDFs fail
- Adds document identification headers for each PDF in the combined output

**Error Handling:**
- Continues processing other PDFs if one fails
- Raises error only if no PDFs could be processed successfully
- Logs warnings for PDFs with no extractable text

---

### 5. `module/services/payroll_rule_service.py`
**Changes:**
- Updated PDF processing step to check for S3 folder path first
- Routes to appropriate method based on input type:
  - `s3_folder_path` → `download_and_extract_text_from_s3_folder()`
  - `pdf_urls` → `download_and_extract_text_from_multiple_pdfs()` (existing)
- Added logging to indicate which input method is being used

**Key Code Changes:**
```python
# Check if S3 folder path is provided, otherwise use URLs
if request.s3_folder_path:
    self.logger.info(f"Processing PDFs from S3 folder: {request.s3_folder_path}")
    pdf_content = self.pdf_service.download_and_extract_text_from_s3_folder(request.s3_folder_path)
else:
    self.logger.info(f"Processing PDFs from URLs: {len(request.pdf_urls)} URL(s)")
    pdf_content = self.pdf_service.download_and_extract_text_from_multiple_pdfs(request.pdf_urls)
```

---

## New Functionality

### S3 Folder Path Format
The S3 folder path can be specified in two formats:
1. `bucket/prefix/` - Simple format (e.g., `payroll-engine-inputs/source-pdfs/IN/`)
2. `s3://bucket/prefix/` - Full S3 URI format (automatically stripped)

### Processing Flow
1. **List PDFs**: Scans the S3 folder for all files ending with `.pdf`
2. **Download**: Downloads each PDF from S3 (with size validation)
3. **Extract**: Extracts text content from each PDF using PyPDF2
4. **Combine**: Merges all extracted text with document separators
5. **Return**: Returns combined text for downstream processing

### Document Identification
Each PDF's extracted text is prefixed with:
```
================================================================================
DOCUMENT X of Y
Source: s3://bucket/key
================================================================================
```

This helps identify which document each section of text came from in the combined output.

---

## Backward Compatibility

✅ **Fully Backward Compatible**
- Existing code using `pdf_urls` continues to work unchanged
- New `s3_folder_path` option is optional
- Validation ensures at least one input method is provided
- No breaking changes to existing API contracts

---

## Dependencies

### New Dependencies
- `boto3` - Already available in AWS Lambda environment (no new dependency needed)

### Configuration
- Uses existing environment variables:
  - `PDF_TIMEOUT_SECONDS` (for URL downloads, not used for S3)
  - `PDF_MAX_SIZE_MB` (applies to both URL and S3 downloads)
- S3 client uses default region: `us-east-1` (hardcoded)

---

## Error Handling

### S3-Specific Errors
- **Invalid path format**: Raises `PDFProcessingError` with descriptive message
- **No PDFs found**: Raises `PDFProcessingError` if folder is empty or contains no PDFs
- **Access denied**: Handles S3 `ClientError` with error code details
- **File not found**: Handles `NoSuchKey` exception
- **File too large**: Validates against `max_file_size` before download
- **Individual PDF failures**: Logs error but continues processing other PDFs

### Validation Errors
- Both `pdf_urls` and `s3_folder_path` cannot be empty
- At least one input method must be provided
- `s3_folder_path` must be a non-empty string if provided

---

## Usage Examples

### Example 1: Using S3 Folder Path
```python
event = {
    "s3_folder_path": "payroll-engine-inputs/source-pdfs/IN/2024/",
    "country": "IN",
    "output_bucket": "payroll-engine-outputs"
}
```

### Example 2: Using Web URLs (Existing)
```python
event = {
    "pdf_urls": [
        "https://example.com/payroll-doc-1.pdf",
        "https://example.com/payroll-doc-2.pdf"
    ],
    "country": "IN",
    "output_bucket": "payroll-engine-outputs"
}
```

---

## Testing Considerations

### Test Cases to Verify
1. ✅ S3 folder path with multiple PDFs
2. ✅ S3 folder path with single PDF
3. ✅ S3 folder path with no PDFs (should error)
4. ✅ Invalid S3 path format (should error)
5. ✅ S3 access denied scenarios
6. ✅ Large PDF files (size validation)
7. ✅ PDFs with no extractable text (should warn but continue)
8. ✅ Mixed success/failure scenarios (some PDFs fail)
9. ✅ Backward compatibility with existing `pdf_urls` input
10. ✅ Both `pdf_urls` and `s3_folder_path` provided (validation)

### S3 Permissions Required
The Lambda execution role needs the following S3 permissions:
- `s3:ListBucket` - To list objects in the folder
- `s3:GetObject` - To download PDF files
- `s3:HeadObject` - To check file size before download

---

## Migration Notes

### For Other Branches
When applying these changes to another branch:

1. **Copy modified files**:
   - `function/dsl_rule_extractor/app.py`
   - `function/intermediate_dsl_generator/app.py`
   - `module/models/extraction_request.py`
   - `module/services/pdf_processing_service.py`
   - `module/services/payroll_rule_service.py`

2. **Verify dependencies**:
   - Ensure `boto3` is available (standard in AWS Lambda)
   - No new package dependencies required

3. **Update IAM permissions**:
   - Ensure Lambda execution role has S3 read permissions for the buckets being accessed

4. **Test both input methods**:
   - Verify existing `pdf_urls` functionality still works
   - Test new `s3_folder_path` functionality

5. **Consider configuration**:
   - Review S3 region hardcoding (`us-east-1`) - may need to make configurable
   - Review `max_file_size` limits for S3 downloads

---

## Potential Improvements (Future)

1. **Region Configuration**: Make S3 region configurable via environment variable
2. **Parallel Processing**: Download and process multiple PDFs in parallel for better performance
3. **Path Validation**: Add more robust S3 path validation
4. **Caching**: Cache S3 folder listings to avoid repeated API calls
5. **Progress Tracking**: Add more detailed progress logging for large folders
6. **Filter Options**: Allow filtering PDFs by name pattern or date

---

## Summary

This implementation adds flexible PDF input handling by supporting both web URLs and S3 folder paths. The changes are backward compatible and maintain the existing API contract while extending functionality. The S3 folder path option is particularly useful for batch processing scenarios where multiple PDFs are stored in a single S3 location.

**Key Benefits:**
- ✅ No breaking changes
- ✅ Supports batch processing from S3 folders
- ✅ Maintains existing URL-based functionality
- ✅ Robust error handling
- ✅ Clear logging and document identification

---

*Document created: Current date*
*Branch: NOVACORE-23001-2*
*Status: Unstaged changes*

