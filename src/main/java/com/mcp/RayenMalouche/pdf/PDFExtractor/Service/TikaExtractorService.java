package com.mcp.RayenMalouche.pdf.PDFExtractor.Service;

import org.apache.tika.Tika;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TikaExtractorService {
    private static final String FILES_DIRECTORY = "files-to-extract";
    private final Tika tika;
    private final AutoDetectParser parser;
    private final Detector detector;

    public TikaExtractorService() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
        this.detector = parser.getDetector();
    }

    /**
     * Extract content from a file and convert to HTML
     */
    public Map<String, Object> extractToHtml(String filename) throws IOException, TikaException, SAXException {
        File file = new File(FILES_DIRECTORY, filename);

        if (!file.exists()) {
            throw new IOException("File not found: " + filename);
        }

        if (!file.canRead()) {
            throw new IOException("Cannot read file: " + filename);
        }

        Map<String, Object> result = new HashMap<>();

        try (InputStream stream = new FileInputStream(file)) {
            // Prepare metadata
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());

            // Use ToHTMLContentHandler for HTML output
            ToHTMLContentHandler htmlHandler = new ToHTMLContentHandler();

            // Parse the document
            ParseContext context = new ParseContext();
            parser.parse(stream, htmlHandler, metadata, context);

            // Get raw HTML
            String html = htmlHandler.toString();

            // Enhance HTML with basic CSS for better formatting (optional improvement for "good" HTML)
            String enhancedHtml = enhanceHtml(html);

            // Build result
            result.put("filename", filename);
            result.put("html", enhancedHtml);
            result.put("contentType", metadata.get(Metadata.CONTENT_TYPE));
            result.put("title", metadata.get(TikaCoreProperties.TITLE));
            result.put("author", metadata.get(TikaCoreProperties.CREATOR));
            result.put("created", metadata.get(TikaCoreProperties.CREATED));
            result.put("modified", metadata.get(TikaCoreProperties.MODIFIED));
            result.put("fileSize", file.length());

            // Add all metadata as additional info
            Map<String, String> allMetadata = new HashMap<>();
            for (String name : metadata.names()) {
                allMetadata.put(name, metadata.get(name));
            }
            result.put("metadata", allMetadata);

            return result;
        }
    }

    /**
     * Extract plain text from a file
     */
    public Map<String, Object> extractText(String filename) throws IOException, TikaException {
        File file = new File(FILES_DIRECTORY, filename);

        if (!file.exists()) {
            throw new IOException("File not found: " + filename);
        }

        Map<String, Object> result = new HashMap<>();

        try (InputStream stream = new FileInputStream(file)) {
            // Detect file type
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());
            MediaType mediaType = detector.detect(stream, metadata);

            // Extract text
            String text = tika.parseToString(file);

            result.put("filename", filename);
            result.put("text", text);
            result.put("mediaType", mediaType.toString());
            result.put("fileSize", file.length());

            return result;
        }
    }

    /**
     * List all files available for extraction
     */
    public Map<String, Object> listAvailableFiles() throws IOException {
        File directory = new File(FILES_DIRECTORY);

        if (!directory.exists()) {
            directory.mkdirs();
            return Map.of(
                    "files", new String[0],
                    "message", "Directory created: " + FILES_DIRECTORY,
                    "path", directory.getAbsolutePath()
            );
        }

        File[] files = directory.listFiles(File::isFile);

        if (files == null || files.length == 0) {
            return Map.of(
                    "files", new String[0],
                    "message", "No files found in " + FILES_DIRECTORY,
                    "path", directory.getAbsolutePath()
            );
        }

        Map<String, Object> fileInfo = new HashMap<>();
        for (File file : files) {
            Map<String, Object> info = new HashMap<>();
            info.put("size", file.length());
            info.put("lastModified", file.lastModified());
            info.put("canRead", file.canRead());

            try {
                String mimeType = tika.detect(file);
                info.put("mimeType", mimeType);
            } catch (IOException e) {
                info.put("mimeType", "unknown");
            }

            fileInfo.put(file.getName(), info);
        }

        return Map.of(
                "files", fileInfo,
                "count", files.length,
                "path", directory.getAbsolutePath()
        );
    }

    /**
     * Get detailed metadata about a file
     */
    public Map<String, Object> getFileMetadata(String filename) throws IOException, TikaException, SAXException {
        File file = new File(FILES_DIRECTORY, filename);

        if (!file.exists()) {
            throw new IOException("File not found: " + filename);
        }

        try (InputStream stream = new FileInputStream(file)) {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getName());

            // Parse to extract metadata
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();
            parser.parse(stream, handler, metadata, context);

            // Convert metadata to map
            Map<String, String> metadataMap = new HashMap<>();
            for (String name : metadata.names()) {
                metadataMap.put(name, metadata.get(name));
            }

            return Map.of(
                    "filename", filename,
                    "metadata", metadataMap,
                    "fileSize", file.length(),
                    "path", file.getAbsolutePath()
            );
        }
    }
    @Test
    public void testPdfExtraction() throws Exception {
        TikaExtractorService service = new TikaExtractorService();
        Map<String, Object> result = service.extractToHtml("sample.pdf");
        assertNotNull(result.get("html"));
        assertEquals("application/pdf", result.get("contentType"));
    }

    // New method to enhance HTML with basic CSS (for better readability)
    private String enhanceHtml(String rawHtml) {
        String css = """
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; margin: 20px; }
                h1, h2, h3 { color: #333; }
                p { margin-bottom: 10px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                img { max-width: 100%; height: auto; }
            </style>
            """;
        // Inject CSS into <head> if present, or add one
        if (rawHtml.contains("<head>")) {
            return rawHtml.replace("<head>", "<head>" + css);
        } else {
            return rawHtml.replace("<html>", "<html><head>" + css + "</head>");
        }
    }
}
