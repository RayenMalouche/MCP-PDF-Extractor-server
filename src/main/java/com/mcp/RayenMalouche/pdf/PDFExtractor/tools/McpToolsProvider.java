package com.mcp.RayenMalouche.pdf.PDFExtractor.tools;

import com.mcp.RayenMalouche.pdf.PDFExtractor.Service.TikaExtractorService;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class McpToolsProvider {

    private final TikaExtractorService extractorService;

    public McpToolsProvider() {
        this.extractorService = new TikaExtractorService();
    }

    public List<McpServerFeatures.SyncToolSpecification> getAllTools() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createExtractToHtmlTool());
        tools.add(createExtractTextTool());
        tools.add(createListFilesTool());
        tools.add(createGetMetadataTool());

        return tools;
    }

    private McpServerFeatures.SyncToolSpecification createExtractToHtmlTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-to-html",
                        "Extract content from a file in the files-to-extract directory and convert it to HTML format",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "filename": {
                              "type": "string",
                              "description": "Name of the file to extract (must be in files-to-extract directory)"
                            }
                          },
                          "required": ["filename"]
                        }
                        """
                ),
                (exchange, params) -> {
                    try {
                        String filename = (String) params.get("filename");

                        if (filename == null || filename.trim().isEmpty()) {
                            return createErrorResult("Filename is required");
                        }

                        System.err.printf("Extracting file to HTML: %s%n", filename);

                        Map<String, Object> result = extractorService.extractToHtml(filename);

                        String response = String.format("""
                        {
                            "status": "success",
                            "filename": "%s",
                            "contentType": "%s",
                            "htmlLength": %d,
                            "html": "%s"
                        }""",
                                escapeJson(filename),
                                escapeJson((String) result.get("contentType")),
                                ((String) result.get("html")).length(),
                                escapeJson((String) result.get("html"))
                        );

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(response)),
                                false
                        );

                    } catch (Exception e) {
                        System.err.println("ERROR in extract-to-html: " + e.getMessage());
                        e.printStackTrace(System.err);
                        return createErrorResult(e.getMessage());
                    }
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification createExtractTextTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "extract-text",
                        "Extract plain text content from a file in the files-to-extract directory",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "filename": {
                              "type": "string",
                              "description": "Name of the file to extract text from"
                            }
                          },
                          "required": ["filename"]
                        }
                        """
                ),
                (exchange, params) -> {
                    try {
                        String filename = (String) params.get("filename");

                        if (filename == null || filename.trim().isEmpty()) {
                            return createErrorResult("Filename is required");
                        }

                        System.err.printf("Extracting text from: %s%n", filename);

                        Map<String, Object> result = extractorService.extractText(filename);

                        String response = String.format("""
                        {
                            "status": "success",
                            "filename": "%s",
                            "mediaType": "%s",
                            "textLength": %d,
                            "text": "%s"
                        }""",
                                escapeJson(filename),
                                escapeJson((String) result.get("mediaType")),
                                ((String) result.get("text")).length(),
                                escapeJson((String) result.get("text"))
                        );

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(response)),
                                false
                        );

                    } catch (Exception e) {
                        System.err.println("ERROR in extract-text: " + e.getMessage());
                        return createErrorResult(e.getMessage());
                    }
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification createListFilesTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "list-files",
                        "List all files available in the files-to-extract directory",
                        """
                        {
                          "type": "object",
                          "properties": {}
                        }
                        """
                ),
                (exchange, params) -> {
                    try {
                        System.err.println("Listing available files");

                        Map<String, Object> result = extractorService.listAvailableFiles();

                        // Convert result to JSON string
                        StringBuilder json = new StringBuilder();
                        json.append("{\n  \"status\": \"success\",\n");
                        json.append("  \"count\": ").append(result.get("count")).append(",\n");
                        json.append("  \"path\": \"").append(escapeJson(result.get("path").toString())).append("\",\n");
                        json.append("  \"files\": ");

                        // Serialize files map
                        Map<String, Object> files = (Map<String, Object>) result.get("files");
                        if (files.isEmpty()) {
                            json.append("{}");
                        } else {
                            json.append("{\n");
                            boolean first = true;
                            for (Map.Entry<String, Object> entry : files.entrySet()) {
                                if (!first) json.append(",\n");
                                json.append("    \"").append(escapeJson(entry.getKey())).append("\": ");

                                Map<String, Object> fileInfo = (Map<String, Object>) entry.getValue();
                                json.append("{\n");
                                json.append("      \"size\": ").append(fileInfo.get("size")).append(",\n");
                                json.append("      \"mimeType\": \"").append(escapeJson(fileInfo.get("mimeType").toString())).append("\"\n");
                                json.append("    }");
                                first = false;
                            }
                            json.append("\n  }");
                        }

                        json.append("\n}");

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(json.toString())),
                                false
                        );

                    } catch (Exception e) {
                        System.err.println("ERROR in list-files: " + e.getMessage());
                        return createErrorResult(e.getMessage());
                    }
                }
        );
    }

    private McpServerFeatures.SyncToolSpecification createGetMetadataTool() {
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "get-file-metadata",
                        "Get detailed metadata information about a file",
                        """
                        {
                          "type": "object",
                          "properties": {
                            "filename": {
                              "type": "string",
                              "description": "Name of the file to get metadata for"
                            }
                          },
                          "required": ["filename"]
                        }
                        """
                ),
                (exchange, params) -> {
                    try {
                        String filename = (String) params.get("filename");

                        if (filename == null || filename.trim().isEmpty()) {
                            return createErrorResult("Filename is required");
                        }

                        System.err.printf("Getting metadata for: %s%n", filename);

                        Map<String, Object> result = extractorService.getFileMetadata(filename);

                        // Build JSON response with metadata
                        StringBuilder json = new StringBuilder();
                        json.append("{\n  \"status\": \"success\",\n");
                        json.append("  \"filename\": \"").append(escapeJson(filename)).append("\",\n");
                        json.append("  \"fileSize\": ").append(result.get("fileSize")).append(",\n");
                        json.append("  \"metadata\": {\n");

                        Map<String, String> metadata = (Map<String, String>) result.get("metadata");
                        boolean first = true;
                        for (Map.Entry<String, String> entry : metadata.entrySet()) {
                            if (!first) json.append(",\n");
                            json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"");
                            json.append(escapeJson(entry.getValue())).append("\"");
                            first = false;
                        }

                        json.append("\n  }\n}");

                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent(json.toString())),
                                false
                        );

                    } catch (Exception e) {
                        System.err.println("ERROR in get-file-metadata: " + e.getMessage());
                        return createErrorResult(e.getMessage());
                    }
                }
        );
    }

    private McpSchema.CallToolResult createErrorResult(String message) {
        String response = String.format("""
            {
                "status": "error",
                "message": "%s"
            }""", escapeJson(message));

        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(response)),
                true
        );
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}