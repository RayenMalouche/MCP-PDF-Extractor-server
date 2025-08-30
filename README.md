# Tika MCP Extractor Server

## Overview

The **Tika MCP Extractor Server** is a Model Context Protocol (MCP) compliant server that uses **Apache Tika** to extract content and metadata from files in various formats (e.g., PDF, DOCX, TXT, HTML, images) stored in a `files-to-extract` directory. It supports conversion to HTML (with optional CSS styling for better readability) or plain text and provides tools to list files and retrieve metadata. Built with **Java 23**, **Spring Boot**, **Jetty**, and the **MCP SDK (0.11.0)**, it integrates with MCP-compliant clients like Claude Desktop or MCP Inspector.

The server exposes **four MCP tools**:
- `extract-to-html`: Converts file content to HTML (with embedded CSS).
- `extract-text`: Extracts plain text.
- `list-available-files`: Lists files in the directory with details.
- `get-file-metadata`: Retrieves detailed file metadata.

It also provides **REST endpoints** for testing, including a new endpoint to serve raw HTML directly for browser rendering. All operations are local, requiring no internet access, making it ideal for secure document processing workflows.

## Features

- **File Extraction**: Converts file content to HTML (with CSS for readability) or plain text using Apache Tika.
- **Metadata Extraction**: Retrieves metadata like title, author, content type, and creation date.
- **File Listing**: Scans `files-to-extract` for files, providing size, MIME type, and modification details.
- **MCP Integration**: Four synchronous tools with JSON schema validation.
- **REST Testing Endpoints**:
  - GET `/api/test/list`: Lists available files.
  - POST `/api/test/extract-html`: Extracts file content as JSON with HTML string.
  - POST `/api/test/extract-text`: Extracts file content as plain text in JSON.
  - POST `/api/test/raw-html`: Serves raw HTML directly (renderable in browsers).
  - GET/POST `/api/health`: Checks server and directory status.
- **CORS Support**: Enabled for all REST endpoints for web-based testing.
- **Configurability**: Settings (port, directory, Tika options) via `application.properties`.
- **Error Handling**: Robust checks for file existence, readability, and parsing errors.
- **Logging**: Console logs with DEBUG support for Tika and PDFBox.

## Prerequisites

- **Java**: JDK 23+ (tested with OpenJDK 24.0.2).
- **Maven**: Version 3.6+ for dependency management and building.
- **Supported File Formats**: PDF, DOCX, TXT, HTML, images, etc., handled by Apache Tika 2.9.1 and PDFBox 2.0.29.
- **Optional**: IntelliJ IDEA for development (output indicates IntelliJ usage, but any IDE or CLI works).
- **Local Files**: Place files in `files-to-extract` directory; no internet required.

## Installation

1. **Clone the Repository** (if hosted):
   ```bash
   git clone <repository-url>
   cd PDFExtractor
   ```

2. **Create the Files Directory**:
  - The server reads from `files-to-extract` (configurable).
  - Create it:
    ```bash
    mkdir files-to-extract
    ```
  - Add sample files (e.g., `sample.pdf`, `document.docx`) for testing.

3. **Build the Project**:
  - Use Maven to compile and resolve dependencies:
    ```bash
    mvn clean install
    ```
  - Outputs executable JAR in `target/`.

## Configuration

Settings are defined in `src/main/resources/application.properties`:

```properties
# Tika MCP Extractor Server Configuration
spring.application.name=TikaExtractorMCPServer

# Server Configuration
server.port=45453

# Tika Configuration
tika.max.string.length=-1
tika.detect.language=false

# File Processing Configuration
files.directory=files-to-extract
files.max.size=52428800

# Logging Configuration
logging.level.org.apache.tika=DEBUG
logging.level.org.apache.pdfbox=DEBUG
```

- **spring.application.name**: Application name for Spring Boot.
- **server.port**: HTTP port (default: 45453).
- **tika.max.string.length**: Sets max string length for Tika (-1 = unlimited).
- **tika.detect.language**: Disables language detection for performance.
- **files.directory**: Directory for input files.
- **files.max.size**: Max file size (50MB).
- **logging.level**: DEBUG for Tika and PDFBox to troubleshoot extraction issues.

The `ConfigLoader` class loads these properties at startup, falling back to defaults if the file is missing or malformed.

## How It Functions

### Architecture
- **Main Class (`PdfExtractorApplication.java`)**:
  - Initializes server, loads config via `ConfigLoader`.
  - Ensures `files-to-extract` exists.
  - Supports HTTP/SSE (default or `--streamable-http`) or STDIO (`--stdio`) modes.
  - Configures Jetty server with MCP transport, test, and health servlets.
- **Service Layer (`TikaExtractorService.java`)**:
  - Core extraction logic using Apache Tika.
  - Methods:
    - `extractToHtml`: Generates HTML with embedded CSS (via `ToHTMLContentHandler`).
    - `extractText`: Extracts plain text using `BodyContentHandler`.
    - `listAvailableFiles`: Scans directory, returns file details (size, MIME, etc.).
    - `getFileMetadata`: Extracts metadata (e.g., `TikaCoreProperties.TITLE`, `CREATOR`).
  - Validates file existence and readability.
- **MCP Tools (`McpToolsProvider.java`)**:
  - Defines four tools with JSON schemas and handlers.
  - Calls `TikaExtractorService` and formats JSON responses (HTML includes CSS).
  - Handles errors with standardized JSON messages.
- **Web Layer**:
  - `TestServlet.java`: REST endpoints for testing, including `/raw-html` for direct HTML rendering.
  - `HealthServlet.java`: Checks server status and directory accessibility.
  - Supports CORS for web clients.
- **Dependencies**:
  - Tika (2.9.1): Parses files; PDFBox (2.0.29) for PDF support.
  - MCP SDK (0.11.0): MCP protocol compliance.
  - Jetty (12.0.18): HTTP server.
  - Jackson (2.15.2): JSON processing.
  - Spring Boot: Manages dependencies and configuration.

### Workflow
1. Place a file (e.g., `sample.pdf`) in `files-to-extract`.
2. Start the server.
3. Use an MCP client to call a tool (e.g., `extract-to-html` with `{"filename": "sample.pdf"}`).
4. Alternatively, use REST endpoints:
  - JSON response: POST `/api/test/extract-html`.
  - Raw HTML: POST `/api/test/raw-html` (renderable in browsers).
5. Server parses the file, returns JSON or HTML with embedded CSS for better formatting.

## Running the Server

### HTTP/SSE Mode
- Default mode for web or MCP Inspector:
  ```bash
  mvn spring-boot:run
  ```
- Streamable HTTP (for MCP Inspector):
  ```bash
  mvn spring-boot:run -- --streamable-http
  ```
- Output:
  ```
  Configuration loaded. Server port: 45453
  Directory exists: files-to-extract
  Starting Tika MCP server with HTTP/SSE transport...
  Tika MCP Extractor Server started on port 45453
  Mode: Standard HTTP/SSE
  MCP endpoint: http://localhost:45453/
  SSE endpoint: http://localhost:45453/sse
  Test endpoints:
    - List files: GET http://localhost:45453/api/test/list
    - Extract HTML: POST http://localhost:45453/api/test/extract-html
    - Extract text: POST http://localhost:45453/api/test/extract-text
    - Raw HTML: POST http://localhost:45453/api/test/raw-html
  Health check: http://localhost:45453/api/health
  Files directory: ./files-to-extract/
  ```

### STDIO Mode
- For command-line or local MCP clients:
  ```bash
  mvn spring-boot:run -- --stdio
  ```

### IDE (IntelliJ)
- Run `PdfExtractorApplication` main method.
- **Native Access Warning**: IntelliJ’s runtime triggers warnings. Ignore or add to VM options:
  ```
  --enable-native-access=ALL-UNNAMED
  ```

Stop with Ctrl+C.

## Usage

### MCP Tools
- **Client**: Use MCP-compliant tools (e.g., MCP Inspector, Claude Desktop).
- **Payload**: JSON with tool parameters:
  ```json
  {
    "filename": "sample.pdf"
  }
  ```
- **Tools**:
  - `extract-to-html`: Returns `{"status": "success", "filename": "...", "contentType": "...", "htmlLength": ..., "html": "..."}` (HTML includes CSS).
  - `extract-text`: Returns plain text in JSON.
  - `list-available-files`: Returns file list with size, MIME, etc.
  - `get-file-metadata`: Returns metadata map.
- **Errors**: `{"status": "error", "message": "..."}`.

### REST Endpoints
Test with CURL, Postman, or browsers:
- **List Files**:
  ```bash
  curl http://localhost:45453/api/test/list
  ```
  Response:
  ```json
  {
    "files": {
      "sample.pdf": {
        "size": 123456,
        "lastModified": 1698765432000,
        "canRead": true,
        "mimeType": "application/pdf"
      }
    },
    "count": 1,
    "path": ".../files-to-extract"
  }
  ```
- **Extract HTML (JSON)**:
  ```bash
  curl -X POST http://localhost:45453/api/test/extract-html \
       -H "Content-Type: application/json" \
       -d '{"filename":"sample.pdf"}'
  ```
  Response:
  ```json
  {
    "filename": "sample.pdf",
    "html": "<html><head><style>body { font-family: Arial, sans-serif; ... }</style></head><body>...</body></html>",
    "contentType": "application/pdf",
    "title": "Sample Document",
    "author": "John Doe"
  }
  ```
- **Extract Raw HTML**:
  ```bash
  curl -X POST http://localhost:45453/api/test/raw-html \
       -H "Content-Type: application/json" \
       -d '{"filename":"sample.pdf"}' > output.html
  ```
  - Open `output.html` in a browser to view styled HTML.
- **Extract Text**:
  ```bash
  curl -X POST http://localhost:45453/api/test/extract-text \
       -H "Content-Type: application/json" \
       -d '{"filename":"sample.pdf"}'
  ```
- **Health Check**:
  ```bash
  curl http://localhost:45453/api/health
  ```
  Response:
  ```json
  {
    "status": "ok",
    "server": "Tika MCP Extractor Server",
    "version": "1.0.0",
    "filesDirectoryExists": true,
    "filesDirectoryReadable": true,
    "filesDirectoryWritable": true
  }
  ```

## Testing

### Unit Tests
- Add JUnit tests in `src/test/java`:
  ```java
  import org.junit.jupiter.api.Test;
  import static org.junit.jupiter.api.Assertions.*;
  import com.mcp.RayenMalouche.pdf.PDFExtractor.Service.TikaExtractorService;

  class TikaExtractorServiceTest {
      @Test
      void testPdfExtraction() throws Exception {
          TikaExtractorService service = new TikaExtractorService();
          Map<String, Object> result = service.extractToHtml("sample.pdf");
          assertNotNull(result.get("html"));
          assertEquals("application/pdf", result.get("contentType"));
          assertTrue(((String) result.get("html")).contains("<style>"));
      }
  }
  ```
- Run:
  ```bash
  mvn test
  ```
- Note: Ensure `sample.pdf` exists in `files-to-extract` for tests.

### Manual Testing
1. Place files in `files-to-extract` (e.g., `sample.pdf`).
2. Start server.
3. Test REST endpoints with CURL/Postman:
  - Verify `/raw-html` renders in browser (save output to `.html` file).
  - Check `/extract-html` for JSON with styled HTML.
4. For MCP, use MCP Inspector or simulate via HTTP POST to `/` or `/message`.
5. Check logs for errors (e.g., "ERROR in extract-to-html").

### Edge Cases
- **Non-existent File**: Returns `{"status": "error", "message": "File not found: ..."}` or HTML error page for `/raw-html`.
- **Large Files**: Limited by `files.max.size` (50MB); adjust in properties.
- **Unsupported Formats**: Tika falls back to text extraction if possible.

## Project Structure

```plaintext
PDFExtractor/
├── src/
│   ├── main/
│   │   ├── java/com/mcp/RayenMalouche/pdf/PDFExtractor/
│   │   │   ├── PdfExtractorApplication.java  # Main entry point
│   │   │   ├── config/
│   │   │   │   └── ConfigLoader.java        # Loads properties
│   │   │   ├── Service/
│   │   │   │   └── TikaExtractorService.java  # Extraction logic
│   │   │   ├── tools/
│   │   │   │   └── McpToolsProvider.java    # MCP tools
│   │   │   ├── web/
│   │   │   │   ├── TestServlet.java         # REST test endpoints
│   │   │   │   └── HealthServlet.java       # Health check
│   │   ├── resources/
│   │   │   └── application.properties       # Configuration
│   ├── test/                                # Add tests here
├── files-to-extract/                        # Input files
├── pom.xml                                  # Maven config
├── target/                                  # Build artifacts
├── README.md                                # This file
```

## Dependencies

From `pom.xml`:
- **Spring Boot (3.5.5)**: Framework foundation.
- **MCP SDK (0.11.0)**: MCP protocol support (note: deprecated APIs).
- **Jetty (12.0.18)**: Embedded HTTP server.
- **Jackson (2.15.2)**: JSON processing.
- **Tika (2.9.1)**: File parsing.
- **PDFBox (2.0.29)**: PDF support (downgraded to fix `NoSuchMethodError`).
- **Commons-IO (2.11.0), Commons-Codec (1.15)**: File utilities.
- Run `mvn dependency:tree` for full list.

## Limitations

- **Deprecated APIs**: MCP SDK 0.11.0 uses deprecated `Tool` constructors. Update to latest SDK when stable.
- **Image Handling**: Embedded images in files (e.g., DOCX) are referenced (e.g., `src="embedded:image1.jpg"`) but not extracted/served.
- **No File Upload**: Files must be manually placed in `files-to-extract`.
- **Performance**: Large files may strain memory; no async processing.
- **Security**: No authentication for endpoints; local use only.
- **Native Access Warning**: IntelliJ runtime triggers warnings—safe to ignore or add `--enable-native-access=ALL-UNNAMED`.

## Future Improvements

- **Image Extraction**: Extract and serve embedded images via a new endpoint.
- **File Upload Endpoint**: Allow dynamic file uploads to `files-to-extract`.
- **Update MCP SDK**: Migrate to latest version to resolve deprecations.
- **Async Processing**: Use reactive streams for large files.
- **Full Spring Boot Integration**: Replace Jetty with Spring’s embedded Tomcat/WebFlux.
- **Authentication**: Add basic auth for REST endpoints.
- **Unit Tests**: Expand test coverage for all components.
- **CI/CD**: Add GitHub Actions for automated builds/tests.

## Troubleshooting

- **Native Access Warning**:
  - IntelliJ-related: `WARNING: java.lang.System::load has been called...`.
  - Fix: Add `--enable-native-access=ALL-UNNAMED` to VM options or ignore.
- **Port Conflict**:
  - Change `server.port` in `application.properties`.
- **File Not Found**:
  - Ensure file exists in `files-to-extract` and matches case.
- **PDF Extraction Errors**:
  - Fixed by downgrading to PDFBox 2.0.29 (resolves `NoSuchMethodError`).
  - Enable `logging.level.org.apache.pdfbox=DEBUG` for diagnostics.
- **Tika Errors**:
  - Verify file format support; update Tika if needed.
- **Build Issues**:
  - Run `mvn clean install`; ensure JDK 23+.
  - Check Maven dependencies for conflicts (`mvn dependency:tree | grep pdfbox`).

## Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/YourFeature`.
3. Commit changes: `git commit -m "Add YourFeature"`.
4. Push: `git push origin feature/YourFeature`.
5. Open a pull request with test cases and docs.

## License

MIT License (recommended; add a `LICENSE` file to the project).

## Contact

- **Maintainer**: Mohamed Rayen Malouche
- **Email**: rayenmalouche27@gmail.com

**Last Updated**: August 30, 2025