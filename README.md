# Tika MCP Extractor Server

## Overview
The **Tika MCP Extractor Server** is a **Model Context Protocol (MCP)** compliant server that leverages **Apache Tika** to extract content and metadata from various file formats (e.g., PDF, DOCX, TXT, HTML, images, etc.) stored in a designated directory (`files-to-extract`). It converts extracted content to **HTML** or **plain text** and provides tools for listing available files and retrieving file metadata. The server supports both **HTTP/SSE** (for web-based integrations like MCP Inspector) and **STDIO** (for command-line or local MCP clients) transports.

Built with **Java 23**, **Spring Boot** (for configuration and dependencies), **Jetty** (embedded server), and the **MCP SDK (version 0.11.0)**, this server acts as a bridge for MCP-compatible AI assistants (e.g., Claude Desktop) to interact with file content. It exposes four MCP tools: **extract-to-html**, **extract-text**, **list-available-files**, and **get-file-metadata**. Additionally, it includes REST endpoints for testing via HTTP, making it easy to verify functionality without an MCP client.

This project is ideal for document processing workflows, content analysis, or integrating file extraction into AI-driven applications. It handles file parsing securely (**no internet access required**) and focuses on **local file operations**.

---

## Features

- **File Extraction to HTML/Text**: Uses Apache Tika to parse files and generate HTML (with structure preservation) or plain text output.
- **Metadata Retrieval**: Extracts detailed metadata (e.g., title, author, content type, creation date) from files.
- **File Listing**: Lists all files in the `files-to-extract` directory with basic info (size, last modified, MIME type).
- **MCP Tools**: Four synchronous tools for MCP integration:
   - `extract-to-html`: Converts file content to HTML.
   - `extract-text`: Extracts plain text content.
   - `list-available-files`: Returns a list of files and their details.
   - `get-file-metadata`: Provides comprehensive file metadata.
- **REST Testing Endpoints**:
   - `GET /api/test/list`: List files.
   - `POST /api/test/extract-html`: Extract to HTML.
   - `POST /api/test/extract-text`: Extract to text.
- **Health Check**: `GET/POST /api/health` to verify server status and directory accessibility.
- **CORS Support**: Enabled for all test endpoints to allow web-based testing (e.g., from browsers or Postman).
- **Configurable**: Settings like port and directory loaded from `application.properties`.
- **Error Handling**: Robust checks for file existence, readability, and parsing errors, with JSON error responses.
- **Logging**: Console logging for server events, tool executions, and errors.

---

## Prerequisites

- **Java**: JDK 23 (or compatible; tested with OpenJDK 24.0.2 in the provided output).
- **Maven**: Version 3.6+ for building and dependency management.
- **IntelliJ IDEA (optional)**: For development; the provided output shows it's run from IntelliJ, but it works in any IDE or command line.
- **Supported File Formats**: Any format handled by Apache Tika (PDF, Word, Excel, images, audio, etc.). For PDFs, Apache PDFBox enhances parsing.
- **No Internet Required**: All operations are local; place files in `files-to-extract`.

> **Note**: The server uses Tika 2.9.1, which supports a wide range of formats but may require updates for newer features.

---

## Installation

### Clone the Repository
```bash
git clone <your-repository-url>
cd PDFExtractor
```

### Create the Files Directory
The server processes files from `./files-to-extract` (configurable). Create it manually:
```bash
mkdir files-to-extract
```
Add sample files (e.g., `sample.pdf`, `document.docx`) to this directory for testing.

### Build the Project
Use Maven to resolve dependencies and compile:
```bash
mvn clean install
```
This generates the executable JAR in `/target`.

---

## Configuration
Configuration is managed via `src/main/resources/application.properties`:
```properties
# Tika MCP Extractor Server Configuration
spring.application.name=TikaExtractorMCPServer

# Server Configuration
server.port=45453  # Default HTTP port; change as needed

# Tika Configuration
tika.max.string.length=-1  # Unlimited string length for large files
tika.detect.language=false  # Disable language detection if not needed

# File Processing Configuration
files.directory=files-to-extract  # Directory for input files
files.max.size=52428800  # Max file size (50MB); adjust for larger files

# Logging Configuration
#logging.level=INFO  # Uncomment and set to DEBUG for verbose logs
```
- **Loading Mechanism**: `ConfigLoader.java` loads these properties at startup. Defaults are used if the file is missing.
- **Customization**: Edit the file and rebuild/restart the server. For example, change `server.port` to `8080` for standard HTTP.

The server ensures the `files.directory` exists on startup; if not, it creates it and logs a message.

---

## How It Functions

### Core Architecture
- **Main Entry Point (`PdfExtractorApplication.java`)**:
   - Loads config via `ConfigLoader`.
   - Creates `files-to-extract` if missing.
   - Determines transport mode from command-line args (`--stdio` or `--streamable-http`).
   - Starts STDIO or HTTP server.
   - In HTTP mode: Uses Jetty to host MCP endpoints, test servlets, and health check.

- **Service Layer (`TikaExtractorService.java`)**:
   - Core logic for extraction using Apache Tika.
   - `extractToHtml(filename)`: Parses file to HTML using `ToHTMLContentHandler`.
   - `extractText(filename)`: Extracts plain text using `BodyContentHandler`.
   - `listAvailableFiles()`: Scans directory for files, includes size, MIME type (detected via Tika).
   - `getFileMetadata(filename)`: Parses file for metadata (e.g., title, author via `TikaCoreProperties`).
   - Handles errors like file not found or unreadable.

- **MCP Integration (`McpToolsProvider.java`)**:
   - Defines four tools, each with schema (JSON object for params) and handler.
   - Handlers call `TikaExtractorService` and format JSON responses.
   - Error results are flagged as true to indicate tool failure.

- **Web Layer**:
   - `TestServlet.java`: Handles REST tests with CORS. Parses JSON requests for filename.
   - `HealthServlet.java`: Returns server status, including directory accessibility.

- **Dependencies**:
   - Tika for parsing; PDFBox for enhanced PDF support.
   - Jackson for JSON handling.
   - MCP SDK for protocol compliance.
   - Jetty for lightweight HTTP server.

### Workflow Example
```bash
# Place sample.pdf in files-to-extract
# Start server
# Use MCP client to call extract-to-html

{
  "filename": "sample.pdf"
}
```
Server parses file, returns JSON with HTML content.

Or test via CURL:
```bash
curl -X POST http://localhost:45453/api/test/extract-html \
  -H "Content-Type: application/json" \
  -d '{"filename":"sample.pdf"}'
```

---

## Running the Server

### HTTP/SSE Mode (Default)
Run from command line:
```bash
mvn spring-boot:run
```
For streamable HTTP (MCP Inspector):
```bash
mvn spring-boot:run -- --streamable-http
```
Access endpoints:
- `http://localhost:45453/` (MCP)
- `/sse` (SSE)
- `/api/test/*` (tests)
- `/api/health` (health)

### STDIO Mode
```bash
mvn spring-boot:run -- --stdio
```
Interact via STDIN/STDOUT for MCP requests.

### From IDE (e.g., IntelliJ)
- Run `PdfExtractorApplication` main method.
- Native access warnings may appear (safe to ignore).
- To suppress warnings: add `--enable-native-access=ALL-UNNAMED` to VM options.

Stop the server with **Ctrl+C**.

---

## Usage

### MCP Tools
Integrate with MCP clients:
- **Payload**: JSON object with tool params (e.g., `{ "filename": "file.txt" }`).
- **Response**: JSON with status, results, or error.

Tools:
- `extract-to-html`: Returns HTML, contentType, etc.
- `extract-text`: Returns text, word count, etc.
- `list-available-files`: Returns file list with info.
- `get-file-metadata`: Returns metadata map.

### REST Endpoints (for Testing)
```bash
# List Files
curl http://localhost:45453/api/test/list

# Extract HTML
curl -X POST http://localhost:45453/api/test/extract-html \
  -H "Content-Type: application/json" \
  -d '{"filename":"sample.pdf"}'

# Extract Text
curl -X POST http://localhost:45453/api/test/extract-text \
  -H "Content-Type: application/json" \
  -d '{"filename":"sample.pdf"}'

# Health
curl http://localhost:45453/api/health
```

---

## Testing

### Unit/Integration Tests
Example JUnit test:
```java
@Test
void testExtractToHtml() throws Exception {
    TikaExtractorService service = new TikaExtractorService();
    Map<String, Object> result = service.extractToHtml("sample.pdf");
    assertEquals("application/pdf", result.get("contentType"));
}
```
Run with:
```bash
mvn test
```

### Manual Testing
- Add files to `files-to-extract`.
- Start server.
- Use CURL for REST endpoints.
- For MCP: Use MCP Inspector or HTTP POST to MCP endpoint.
- Check logs for errors.

### Edge Cases
- **Non-existent file**: Error response.
- **Large files**: Respect `files.max.size`.
- **Unsupported formats**: Tika falls back to text extraction.

---

## Project Structure
```text
PDFExtractor/
├── src/
│   ├── main/
│   │   ├── java/com/mcp/RayenMalouche/pdf/PDFExtractor/
│   │   │   ├── PdfExtractorApplication.java  # Main server starter
│   │   │   ├── config/ConfigLoader.java     # Properties loader
│   │   │   ├── Service/TikaExtractorService.java  # Extraction logic
│   │   │   ├── tools/McpToolsProvider.java  # MCP tools definition
│   │   │   ├── web/TestServlet.java         # REST test endpoints
│   │   │   ├── web/HealthServlet.java       # Health check
│   │   ├── resources/application.properties # Config file
│   ├── test/                                # Add tests here
├── files-to-extract/                        # Input files directory
├── pom.xml                                  # Maven dependencies
├── README.md                                # This file
├── target/                                  # Build artifacts
```

---

## Dependencies
From `pom.xml`:
- Spring Boot: Core framework.
- MCP SDK (0.11.0): MCP protocol.
- Jetty (12.0.18): HTTP server.
- Jackson (2.15.2): JSON.
- Tika (2.9.1): File parsing.
- PDFBox (3.0.1): PDF support.
- Commons-IO/Code (2.11.0/1.15): Utilities.

Run:
```bash
mvn dependency:tree
```

---

## Limitations
- Deprecated APIs: MCP SDK 0.11.0 has deprecations (e.g., tool constructors)—update to latest.
- Image Handling: Embedded images referenced but not extracted/served.
- No File Upload: Files must be manually placed in directory.
- Performance: Large files may slow down; no async processing.
- Security: Local-only; no auth for endpoints.

---

## Future Improvements
- Add file upload endpoint.
- Extract/serve embedded images.
- Update MCP SDK to latest (resolve deprecations).
- Add async support for large files.
- Integrate Spring Boot fully for better config/web management.
- Add unit tests and CI/CD.

---

## Troubleshooting
- **Native Access Warnings**: IntelliJ-related; add `--enable-native-access=ALL-UNNAMED` or ignore.
- **Port in Use**: Change `server.port` in properties.
- **File Not Found**: Ensure files exist in `files-to-extract`.
- **Tika Errors**: Update Tika if format unsupported.
- **Logs**: Check console (e.g., `ERROR in extract-to-html`).
- **Build Issues**: Run `mvn clean install`; ensure JDK 23+.

---

## License
MIT License (assumed; add LICENSE file).

---

## Contact
Maintainer: **Mohamed Rayen Malouche**  
Email: **rayenmalouche27@gmail.com**

_Last Updated: August 30, 2025_

