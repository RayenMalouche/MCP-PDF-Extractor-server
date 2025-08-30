# PDF Extraction MCP Server

A Model Context Protocol (MCP) server that provides comprehensive PDF data extraction capabilities through Apache Tika and PDFBox integration. This server acts as a bridge between MCP-compatible AI assistants (like Claude Desktop) and PDF processing services.

## Features

### Extraction Capabilities
- **Text Extraction**: Plain text content from PDF pages with page-by-page breakdown
- **Image Extraction**: Extract and save embedded images from PDFs
- **Table Extraction**: Detect and extract tabular data (basic implementation)
- **Form Field Extraction**: Extract form fields, their types, values, and properties
- **Metadata Extraction**: Document metadata including author, title, creation date, etc.
- **Full Extraction**: Complete extraction of all above components in one operation

### Output Formats
- **JSON**: Structured data format for programmatic use
- **Markdown**: Human-readable format with proper formatting
- **Plain Text**: Simple text output for basic needs
- **HTML**: Rich formatted output with styling

### Input Methods
- **File Path**: Relative paths to files in the `files-to-extract` directory
- **Absolute Path**: Full system paths to PDF files
- **Directory Listing**: List all available PDFs in the input directory

## Project Structure

```
PDFExtractor/
├── src/main/java/com/pdf/mcp/extraction/server/PDFExtractor/
│   ├── PDFExtractorApplication.java          # Main MCP server application
│   ├── Config/
│   │   └── ConfigLoader.java                 # Configuration management
│   └── Service/
│       ├── PDFExtractionService.java        # Core PDF processing logic
│       └── PDFUtilityTool.java              # Utility functions
├── src/main/resources/
│   └── application.properties                # Server configuration
├── files-to-extract/                        # Input directory for PDF files
├── extracted_data/                          # Output directory for results
│   ├── images/                              # Extracted images
│   ├── text/                                # Text extraction results
│   ├── tables/                              # Table extraction results
│   ├── forms/                               # Form field results
│   └── metadata/                            # Metadata results
└── pom.xml                                  # Maven dependencies
```

## Setup and Installation

### Prerequisites
- Java 23 or higher
- Maven 3.6+
- Apache Tika and PDFBox (included as dependencies)

### Installation Steps

1. **Clone/Create the project structure** with all the provided files

2. **Install dependencies**:
   ```bash
   mvn clean install
   ```

3. **Create directories**:
   ```bash
   mkdir -p files-to-extract
   mkdir -p extracted_data
   ```

4. **Configure the server** by editing `src/main/resources/application.properties`:
   ```properties
   # Adjust paths and limits as needed
   pdf.input.directory=files-to-extract
   pdf.output.directory=extracted_data
   pdf.max.file.size=50MB
   pdf.max.pages=1000
   ```

## Running the Server

### STDIO Mode (for Claude Desktop)
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--stdio"
```

### HTTP Mode (for web testing)
```bash
mvn spring-boot:run
```
Server will start on `http://localhost:45451`

### Streamable HTTP Mode (for MCP Inspector)
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--streamable-http"
```

## Available Tools

### 1. `list-available-pdfs`
Lists all PDF files in the `files-to-extract` directory.

**Parameters:**
- `outputFormat` (optional): `json`, `markdown`, `plaintext`, or `html`

**Example:**
```json
{
  "outputFormat": "markdown"
}
```

### 2. `extract-pdf-text`
Extracts text content from PDF files.

**Parameters:**
- `filePath`: Path to PDF file (relative to files-to-extract or absolute)
- `outputFormat` (optional): `json`, `markdown`, `plaintext`, or `html`
- `pageRange` (optional): `"all"`, `"1-5"`, `"2,4,6"`, etc.

**Example:**
```json
{
  "filePath": "document.pdf",
  "outputFormat": "html",
  "pageRange": "1-3"
}
```

### 3. `extract-pdf-images`
Extracts images from PDF files.

**Parameters:**
- `filePath`: Path to PDF file
- `outputFormat` (optional): `json`, `markdown`, `plaintext`, or `html`
- `imageFormat` (optional): `png`, `jpg`, or `gif`
- `pageRange` (optional): Page range specification

### 4. `extract-pdf-tables`
Extracts table data from PDF files.

**Parameters:**
- `filePath`: Path to PDF file
- `outputFormat` (optional): Output format
- `pageRange` (optional): Page range specification

### 5. `extract-pdf-forms`
Extracts form field data from PDF files.

**Parameters:**
- `filePath`: Path to PDF file
- `outputFormat` (optional): Output format

### 6. `extract-pdf-metadata`
Extracts metadata from PDF files.

**Parameters:**
- `filePath`: Path to PDF file
- `outputFormat` (optional): Output format

### 7. `extract-pdf-full`
Performs complete extraction of all components.

**Parameters:**
- `filePath`: Path to PDF file
- `outputFormat` (optional): Output format
- `pageRange` (optional): Page range specification

## Usage Examples

### Basic Text Extraction
1. Place your PDF in the `files-to-extract` directory
2. Use the MCP tool:
   ```json
   {
     "tool": "extract-pdf-text",
     "parameters": {
       "filePath": "my-document.pdf",
       "outputFormat": "markdown"
     }
   }
   ```

### Complete Extraction with HTML Output
```json
{
  "tool": "extract-pdf-full",
  "parameters": {
    "filePath": "complex-document.pdf",
    "outputFormat": "html",
    "pageRange": "all"
  }
}
```

### List Available Files
```json
{
  "tool": "list-available-pdfs",
  "parameters": {
    "outputFormat": "html"
  }
}
```

## Configuration

### application.properties Options
```properties
# Directories
pdf.input.directory=files-to-extract
pdf.output.directory=extracted_data

# Processing Limits
pdf.max.file.size=50MB
pdf.max.pages=1000
pdf.enable.ocr=true

# Server Settings
server.port=45451
server.name=PDF Extraction MCP Server
server.version=1.0.0

# Output Formats
output.format.json=true
output.format.markdown=true
output.format.plaintext=true
output.format.html=true

# Image Settings
images.output.format=png
images.max.width=2048
images.max.height=2048
images.quality=0.9
```

## API Endpoints (HTTP Mode)

- **Health Check**: `GET/POST /api/health`
- **Test Extraction**: `POST /api/test-extraction`
- **MCP Protocol**: `/` (standard) or `/message` (streamable)
- **Server-Sent Events**: `/sse`

## Error Handling

The server provides detailed error messages for common issues:
- File not found
- Invalid PDF format
- File size exceeds limits
- Page count exceeds limits
- Unsupported output format
- Processing errors

## Performance Considerations

- **File Size Limit**: Default 50MB (configurable)
- **Page Limit**: Default 1000 pages (configurable)
- **Memory Usage**: Depends on PDF complexity and image count
- **Processing Time**: Varies with extraction type and document size

## Dependencies

### Core Libraries
- **Apache Tika 2.9.1**: Document parsing and text extraction
- **Apache PDFBox 3.0.1**: PDF-specific operations and image extraction
- **MCP SDK 0.11.0**: Model Context Protocol implementation
- **Spring Boot 3.5.5**: Application framework
- **Jackson 2.15.2**: JSON processing

### Supporting Libraries
- **Jetty 12.0.18**: HTTP server
- **Commons IO 2.11.0**: File operations
- **Commons Codec 1.15**: Base64 encoding

## Troubleshooting

### Common Issues

1. **"File not found" errors**:
    - Ensure PDF is in `files-to-extract` directory
    - Check file permissions
    - Verify file path spelling

2. **Memory issues with large PDFs**:
    - Reduce `pdf.max.file.size`
    - Process files in smaller page ranges
    - Increase JVM heap size: `-Xmx2g`

3. **Image extraction fails**:
    - Some PDFs have embedded images that can't be extracted
    - Try different image formats (png, jpg, gif)

4. **Table extraction is incomplete**:
    - Current implementation is basic
    - Consider using specialized libraries like Tabula for complex tables

### Logs and Debugging
- Server logs errors to `System.err`
- Extraction progress logged for each operation
- HTTP requests logged in standard format

## License

This project uses various open-source libraries. Please check individual license requirements:
- Apache Tika: Apache License 2.0
- Apache PDFBox: Apache License 2.0
- Spring Boot: Apache License 2.0