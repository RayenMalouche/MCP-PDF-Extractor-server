package com.mcp.RayenMalouche.pdf.PDFExtractor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.mcp.RayenMalouche.pdf.PDFExtractor.config.ConfigLoader;
import com.mcp.RayenMalouche.pdf.PDFExtractor.Service.PDFExtractionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class PdfExtractorApplication {
    // Configuration loaded from application.properties
    private static String OUTPUT_DIRECTORY;
    private static long MAX_FILE_SIZE;
    private static int MAX_PAGES;
    private static boolean OCR_ENABLED;
    private static int SERVER_PORT;
    private static String SERVER_NAME;
    private static String SERVER_VERSION;

    // PDF Extraction Service
    private static PDFExtractionService extractionService;

    public static void main(String[] args) throws Exception {
        // Load configuration
        loadConfiguration();

        // Initialize extraction service
        extractionService = new PDFExtractionService();

        // Create output directory if it doesn't exist
        createOutputDirectory();

        // Check transport type
        boolean useStdio = args.length > 0 && "--stdio".equals(args[0]);
        boolean useStreamableHttp = args.length > 0 && "--streamable-http".equals(args[0]);

        if (useStdio) {
            System.err.println("Starting PDF MCP server with STDIO transport...");
            startStdioServer();
        } else {
            System.out.println("Starting PDF MCP server with HTTP/SSE transport...");
            startHttpServer(useStreamableHttp);
        }
    }

    private static void loadConfiguration() {
        OUTPUT_DIRECTORY = ConfigLoader.getProperty("pdf.output.directory", "extracted_data");
        MAX_FILE_SIZE = ConfigLoader.parseSizeProperty("pdf.max.file.size", 50 * 1024 * 1024); // 50MB default
        MAX_PAGES = ConfigLoader.getIntProperty("pdf.max.pages", 1000);
        OCR_ENABLED = ConfigLoader.getBooleanProperty("pdf.enable.ocr", true);
        SERVER_PORT = ConfigLoader.getIntProperty("server.port", 45451);
        SERVER_NAME = ConfigLoader.getProperty("server.name", "PDF Extraction MCP Server");
        SERVER_VERSION = ConfigLoader.getProperty("server.version", "1.0.0");

        System.err.println("PDF Extraction configuration loaded from application.properties");
        System.err.println("Output directory: " + OUTPUT_DIRECTORY);
        System.err.println("Max file size: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        System.err.println("Max pages: " + MAX_PAGES);
    }

    private static void createOutputDirectory() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIRECTORY);
            Files.createDirectories(outputPath);
            System.err.println("Output directory created/verified: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create output directory: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void startStdioServer() {
        try {
            System.err.println("Initializing STDIO PDF MCP server...");

            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new ObjectMapper());

            McpSyncServer syncServer = McpServer.sync(transportProvider)
                    .serverInfo("pdf-extraction-server", SERVER_VERSION)
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .logging()
                            .build())
                    .tools(createPDFExtractionTools())
                    .build();

            System.err.println("STDIO PDF MCP server started. Awaiting requests...");

        } catch (Exception e) {
            System.err.println("Fatal error in STDIO server: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void startHttpServer(boolean streamableHttp) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        HttpServletSseServerTransportProvider transportProvider;
        if (streamableHttp) {
            transportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/message", "/sse");
        } else {
            transportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/", "/sse");
        }

        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("pdf-extraction-server", SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(createPDFExtractionTools())
                .build();

        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("pdf-mcp-server");

        Server server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(SERVER_PORT);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        context.addServlet(new ServletHolder(transportProvider), "/*");
        context.addServlet(new ServletHolder(new PDFTestServlet()), "/api/test-extraction");
        context.addServlet(new ServletHolder(new HealthServlet()), "/api/health");

        server.setHandler(context);
        server.start();

        System.err.println("=================================");
        System.err.println(SERVER_NAME + " started on port " + SERVER_PORT);
        if (streamableHttp) {
            System.err.println("Mode: Streamable HTTP (for MCP Inspector)");
            System.err.println("MCP endpoint: http://localhost:" + SERVER_PORT + "/message");
        } else {
            System.err.println("Mode: Standard HTTP/SSE");
            System.err.println("MCP endpoint: http://localhost:" + SERVER_PORT + "/");
        }
        System.err.println("SSE endpoint: http://localhost:" + SERVER_PORT + "/sse");
        System.err.println("Test endpoint: http://localhost:" + SERVER_PORT + "/api/test-extraction");
        System.err.println("Health check: http://localhost:" + SERVER_PORT + "/api/health");
        System.err.println("=================================");
        server.join();
    }

    private static List<McpServerFeatures.SyncToolSpecification> createPDFExtractionTools() {
        return Arrays.asList(
                createTextExtractionTool(),
                createImageExtractionTool(),
                createTableExtractionTool(),
                createFormFieldExtractionTool(),
                createMetadataExtractionTool(),
                createFullExtractionTool()
        );
    }

    private static McpServerFeatures.SyncToolSpecification createTextExtractionTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-pdf-text",
                        "Extracts plain text content from PDF files",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filePath": {
                                      "type": "string",
                                      "description": "Path to the PDF file"
                                    },
                                    "outputFormat": {
                                      "type": "string",
                                      "description": "Output format: json, markdown, or plaintext",
                                      "enum": ["json", "markdown", "plaintext"],
                                      "default": "json"
                                    },
                                    "pageRange": {
                                      "type": "string",
                                      "description": "Page range (e.g., '1-5', '2,4,6', or 'all')",
                                      "default": "all"
                                    }
                                  },
                                  "required": ["filePath"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filePath = (String) params.get("filePath");
                        String outputFormat = (String) params.getOrDefault("outputFormat", "json");
                        String pageRange = (String) params.getOrDefault("pageRange", "all");

                        System.err.printf("Executing extract-pdf-text: file=%s, format=%s, pages=%s%n",
                                filePath, outputFormat, pageRange);

                        String result = extractionService.extractText(filePath, outputFormat, pageRange);

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-pdf-text: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(createErrorResponse(e))),
                                true
                        );
                    }
                }
        );
    }

    private static McpServerFeatures.SyncToolSpecification createImageExtractionTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-pdf-images",
                        "Extracts images from PDF files and saves them to the output directory",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filePath": {
                                      "type": "string",
                                      "description": "Path to the PDF file"
                                    },
                                    "outputFormat": {
                                      "type": "string",
                                      "description": "Output format for results: json, markdown, or plaintext",
                                      "enum": ["json", "markdown", "plaintext"],
                                      "default": "json"
                                    },
                                    "imageFormat": {
                                      "type": "string",
                                      "description": "Image format: png, jpg, or gif",
                                      "enum": ["png", "jpg", "gif"],
                                      "default": "png"
                                    },
                                    "pageRange": {
                                      "type": "string",
                                      "description": "Page range (e.g., '1-5', '2,4,6', or 'all')",
                                      "default": "all"
                                    }
                                  },
                                  "required": ["filePath"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filePath = (String) params.get("filePath");
                        String outputFormat = (String) params.getOrDefault("outputFormat", "json");
                        String imageFormat = (String) params.getOrDefault("imageFormat", "png");
                        String pageRange = (String) params.getOrDefault("pageRange", "all");

                        System.err.printf("Executing extract-pdf-images: file=%s, format=%s, imgFormat=%s, pages=%s%n",
                                filePath, outputFormat, imageFormat, pageRange);

                        String result = extractionService.extractImages(filePath, outputFormat, imageFormat, pageRange);

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-pdf-images: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(createErrorResponse(e))),
                                true
                        );
                    }
                }
        );
    }

    private static McpServerFeatures.SyncToolSpecification createTableExtractionTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-pdf-tables",
                        "Extracts table data from PDF files",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filePath": {
                                      "type": "string",
                                      "description": "Path to the PDF file"
                                    },
                                    "outputFormat": {
                                      "type": "string",
                                      "description": "Output format: json, markdown, or plaintext",
                                      "enum": ["json", "markdown", "plaintext"],
                                      "default": "json"
                                    },
                                    "pageRange": {
                                      "type": "string",
                                      "description": "Page range (e.g., '1-5', '2,4,6', or 'all')",
                                      "default": "all"
                                    }
                                  },
                                  "required": ["filePath"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filePath = (String) params.get("filePath");
                        String outputFormat = (String) params.getOrDefault("outputFormat", "json");
                        String pageRange = (String) params.getOrDefault("pageRange", "all");

                        System.err.printf("Executing extract-pdf-tables: file=%s, format=%s, pages=%s%n",
                                filePath, outputFormat, pageRange);

                        String result = extractionService.extractTables(filePath, outputFormat, pageRange);

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-pdf-tables: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(createErrorResponse(e))),
                                true
                        );
                    }
                }
        );
    }

    private static McpServerFeatures.SyncToolSpecification createFormFieldExtractionTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-pdf-forms",
                        "Extracts form field data from PDF files",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filePath": {
                                      "type": "string",
                                      "description": "Path to the PDF file"
                                    },
                                    "outputFormat": {
                                      "type": "string",
                                      "description": "Output format: json, markdown, or plaintext",
                                      "enum": ["json", "markdown", "plaintext"],
                                      "default": "json"
                                    }
                                  },
                                  "required": ["filePath"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filePath = (String) params.get("filePath");
                        String outputFormat = (String) params.getOrDefault("outputFormat", "json");

                        System.err.printf("Executing extract-pdf-forms: file=%s, format=%s%n",
                                filePath, outputFormat);

                        String result = extractionService.extractFormFields(filePath, outputFormat);

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-pdf-forms: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(createErrorResponse(e))),
                                true
                        );
                    }
                }
        );
    }

    private static McpServerFeatures.SyncToolSpecification createMetadataExtractionTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-pdf-metadata",
                        "Extracts metadata from PDF files (author, title, creation date, etc.)",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filePath": {
                                      "type": "string",
                                      "description": "Path to the PDF file"
                                    },
                                    "outputFormat": {
                                      "type": "string",
                                      "description": "Output format: json, markdown, or plaintext",
                                      "enum": ["json", "markdown", "plaintext"],
                                      "default": "json"
                                    }
                                  },
                                  "required": ["filePath"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filePath = (String) params.get("filePath");
                        String outputFormat = (String) params.getOrDefault("outputFormat", "json");

                        System.err.printf("Executing extract-pdf-metadata: file=%s, format=%s%n",
                                filePath, outputFormat);

                        String result = extractionService.extractMetadata(filePath, outputFormat);

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-pdf-metadata: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(createErrorResponse(e))),
                                true
                        );
                    }
                }
        );
    }

    private static McpServerFeatures.SyncToolSpecification createFullExtractionTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-pdf-full",
                        "Performs complete extraction: text, images, tables, forms, and metadata",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "filePath": {
                                      "type": "string",
                                      "description": "Path to the PDF file"
                                    },
                                    "outputFormat": {
                                      "type": "string",
                                      "description": "Output format: json, markdown, or plaintext",
                                      "enum": ["json", "markdown", "plaintext"],
                                      "default": "json"
                                    },
                                    "pageRange": {
                                      "type": "string",
                                      "description": "Page range (e.g., '1-5', '2,4,6', or 'all')",
                                      "default": "all"
                                    }
                                  },
                                  "required": ["filePath"]
                                }
                                """
                ),
                (exchange, params) -> {
                    try {
                        String filePath = (String) params.get("filePath");
                        String outputFormat = (String) params.getOrDefault("outputFormat", "json");
                        String pageRange = (String) params.getOrDefault("pageRange", "all");

                        System.err.printf("Executing extract-pdf-full: file=%s, format=%s, pages=%s%n",
                                filePath, outputFormat, pageRange);

                        String result = extractionService.extractFull(filePath, outputFormat, pageRange);

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(result)),
                                false
                        );
                    } catch (Exception e) {
                        System.err.println("ERROR in extract-pdf-full: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(createErrorResponse(e))),
                                true
                        );
                    }
                }
        );
    }

    // Test servlet for PDF extraction via REST
    public static class PDFTestServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type");

            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader reader = req.getReader();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> requestData = mapper.readValue(sb.toString(), Map.class);

                String filePath = (String) requestData.get("filePath");
                String extractionType = (String) requestData.getOrDefault("extractionType", "text");
                String outputFormat = (String) requestData.getOrDefault("outputFormat", "json");
                String pageRange = (String) requestData.getOrDefault("pageRange", "all");

                if (filePath == null) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"status\": \"error\", \"message\": \"Missing required field: filePath\"}");
                    return;
                }

                String result;
                switch (extractionType.toLowerCase()) {
                    case "text":
                        result = extractionService.extractText(filePath, outputFormat, pageRange);
                        break;
                    case "images":
                        result = extractionService.extractImages(filePath, outputFormat, "png", pageRange);
                        break;
                    case "tables":
                        result = extractionService.extractTables(filePath, outputFormat, pageRange);
                        break;
                    case "forms":
                        result = extractionService.extractFormFields(filePath, outputFormat);
                        break;
                    case "metadata":
                        result = extractionService.extractMetadata(filePath, outputFormat);
                        break;
                    case "full":
                        result = extractionService.extractFull(filePath, outputFormat, pageRange);
                        break;
                    default:
                        resp.setStatus(400);
                        resp.getWriter().write("{\"status\": \"error\", \"message\": \"Invalid extraction type\"}");
                        return;
                }

                resp.getWriter().write(result);

            } catch (Exception e) {
                resp.setStatus(500);
                resp.getWriter().write(String.format("{\"status\": \"error\", \"message\": \"%s\"}",
                        e.getMessage().replace("\"", "\\\"")));
            }
        }

        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
            resp.setStatus(200);
        }
    }

    // Health check servlet
    public static class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            sendHealthResponse(resp);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            sendHealthResponse(resp);
        }

        private void sendHealthResponse(HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.getWriter().write(String.format(
                    "{\"status\": \"healthy\", \"server\": \"%s\", \"version\": \"%s\", \"outputDirectory\": \"%s\"}",
                    SERVER_NAME, SERVER_VERSION, OUTPUT_DIRECTORY));
        }
    }

    private static String createErrorResponse(Exception e) {
        return String.format("""
                        {
                            "status": "error",
                            "message": "Failed to process PDF: %s",
                            "errorType": "%s"
                        }""",
                escapeJsonString(e.getMessage()),
                escapeJsonString(e.getClass().getSimpleName()));
    }

    private static String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
