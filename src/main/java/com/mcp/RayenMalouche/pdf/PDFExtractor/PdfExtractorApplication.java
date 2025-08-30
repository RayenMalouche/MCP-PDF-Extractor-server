package com.mcp.RayenMalouche.pdf.PDFExtractor;

import com.mcp.RayenMalouche.pdf.PDFExtractor.config.ConfigLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.RayenMalouche.pdf.PDFExtractor.config.ConfigLoader;
import com.mcp.RayenMalouche.pdf.PDFExtractor.Service.TikaExtractorService;
import com.mcp.RayenMalouche.pdf.PDFExtractor.tools.McpToolsProvider;
import com.mcp.RayenMalouche.pdf.PDFExtractor.web.TestServlet;
import com.mcp.RayenMalouche.pdf.PDFExtractor.web.HealthServlet;

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

import java.io.File;
import java.util.List;

public class PdfExtractorApplication{
    private static final String VERSION = "1.0.0";
    private static final String SERVER_NAME = "tika-extractor-server";
    private static int HTTP_PORT;

    public static void main(String[] args) throws Exception {
        // Load configuration
        loadConfiguration();

        // Ensure files-to-extract directory exists
        ensureDirectoryExists();

        // Check transport mode
        boolean useStdio = args.length > 0 && "--stdio".equals(args[0]);
        boolean useStreamableHttp = args.length > 0 && "--streamable-http".equals(args[0]);

        if (useStdio) {
            System.err.println("Starting Tika MCP server with STDIO transport...");
            startStdioServer();
        } else {
            System.out.println("Starting Tika MCP server with HTTP/SSE transport...");
            startHttpServer(useStreamableHttp);
        }
    }

    private static void loadConfiguration() {
        HTTP_PORT = ConfigLoader.getIntProperty("server.port", 45451);
        System.err.println("Configuration loaded. Server port: " + HTTP_PORT);
    }

    private static void ensureDirectoryExists() {
        File directory = new File("files-to-extract");
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                System.err.println("Created directory: files-to-extract");
            } else {
                System.err.println("Warning: Could not create files-to-extract directory");
            }
        } else {
            System.err.println("Directory exists: files-to-extract");
        }
    }

    private static void startStdioServer() {
        try {
            System.err.println("Initializing STDIO Tika MCP server...");

            // Create transport provider
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new ObjectMapper());

            // Get all tools
            McpToolsProvider toolsProvider = new McpToolsProvider();
            List<McpServerFeatures.SyncToolSpecification> tools = toolsProvider.getAllTools();

            // Build MCP server
            McpSyncServer syncServer = McpServer.sync(transportProvider)
                    .serverInfo(SERVER_NAME, VERSION)
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .logging()
                            .build())
                    .tools(tools.toArray(new McpServerFeatures.SyncToolSpecification[0]))
                    .build();

            System.err.println("STDIO Tika MCP server started. Awaiting requests...");
            System.err.println("Available tools: extract-to-html, extract-text, list-files, get-file-metadata");

        } catch (Exception e) {
            System.err.println("Fatal error in STDIO server: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void startHttpServer(boolean streamableHttp) throws Exception {
        // Create ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        // Create SSE transport provider
        HttpServletSseServerTransportProvider transportProvider;

        if (streamableHttp) {
            transportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/message", "/sse");
        } else {
            transportProvider = new HttpServletSseServerTransportProvider(objectMapper, "/", "/sse");
        }

        // Get all tools
        McpToolsProvider toolsProvider = new McpToolsProvider();
        List<McpServerFeatures.SyncToolSpecification> tools = toolsProvider.getAllTools();

        // Build MCP server
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(tools.toArray(new McpServerFeatures.SyncToolSpecification[0]))
                .build();

        // Configure Jetty server
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("tika-mcp-server");

        Server server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(HTTP_PORT);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        // Add MCP transport servlet
        context.addServlet(new ServletHolder(transportProvider), "/*");

        // Add test and health servlets
        TikaExtractorService extractorService = new TikaExtractorService();
        context.addServlet(new ServletHolder(new TestServlet(extractorService)), "/api/test/*");
        context.addServlet(new ServletHolder(new HealthServlet()), "/api/health");

        server.setHandler(context);

        // Start server
        server.start();

        System.err.println("=================================");
        System.err.println("Tika MCP Extractor Server started on port " + HTTP_PORT);
        if (streamableHttp) {
            System.err.println("Mode: Streamable HTTP (for MCP Inspector)");
            System.err.println("MCP endpoint: http://localhost:" + HTTP_PORT + "/message");
        } else {
            System.err.println("Mode: Standard HTTP/SSE");
            System.err.println("MCP endpoint: http://localhost:" + HTTP_PORT + "/");
        }
        System.err.println("SSE endpoint: http://localhost:" + HTTP_PORT + "/sse");
        System.err.println("Test endpoints:");
        System.err.println("  - List files: GET http://localhost:" + HTTP_PORT + "/api/test/list");
        System.err.println("  - Extract HTML: POST http://localhost:" + HTTP_PORT + "/api/test/extract-html");
        System.err.println("  - Extract text: POST http://localhost:" + HTTP_PORT + "/api/test/extract-text");
        System.err.println("Health check: http://localhost:" + HTTP_PORT + "/api/health");
        System.err.println("Files directory: ./files-to-extract/");
        System.err.println("=================================");

        server.join();
    }

}
