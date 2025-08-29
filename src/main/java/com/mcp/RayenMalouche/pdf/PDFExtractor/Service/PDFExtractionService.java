package com.mcp.RayenMalouche.pdf.PDFExtractor.Service;

import com.mcp.RayenMalouche.pdf.PDFExtractor.config.ConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class PDFExtractionService {
    private final ObjectMapper objectMapper;
    private final String outputDirectory;
    private final long maxFileSize;
    private final int maxPages;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PDFExtractionService() {
        this.objectMapper = new ObjectMapper();
        this.outputDirectory = ConfigLoader.getProperty("pdf.output.directory", "extracted_data");
        this.maxFileSize = ConfigLoader.parseSizeProperty("pdf.max.file.size", 50 * 1024 * 1024);
        this.maxPages = ConfigLoader.getIntProperty("pdf.max.pages", 1000);

        // Create subdirectories
        try {
            Files.createDirectories(Paths.get(outputDirectory, "images"));
            Files.createDirectories(Paths.get(outputDirectory, "text"));
            Files.createDirectories(Paths.get(outputDirectory, "tables"));
            Files.createDirectories(Paths.get(outputDirectory, "forms"));
            Files.createDirectories(Paths.get(outputDirectory, "metadata"));
        } catch (IOException e) {
            System.err.println("Warning: Could not create subdirectories: " + e.getMessage());
        }
    }

    public String extractText(String filePath, String outputFormat, String pageRange) throws Exception {
        validateFile(filePath);

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            List<Integer> pages = parsePageRange(pageRange, document.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();
            Map<Integer, String> pageTexts = new HashMap<>();

            for (Integer pageNum : pages) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);
                pageTexts.put(pageNum, pageText);
                fullText.append("=== Page ").append(pageNum).append(" ===\n");
                fullText.append(pageText).append("\n\n");
            }

            // Save to file
            String timestamp = String.valueOf(System.currentTimeMillis());
            String baseFileName = getBaseFileName(filePath) + "_text_" + timestamp;
            Path textFile = Paths.get(outputDirectory, "text", baseFileName + ".txt");
            Files.write(textFile, fullText.toString().getBytes());

            return formatTextResult(pageTexts, fullText.toString(), textFile.toString(), outputFormat);

        } catch (Exception e) {
            throw new Exception("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    public String extractImages(String filePath, String outputFormat, String imageFormat, String pageRange) throws Exception {
        validateFile(filePath);

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            List<Integer> pages = parsePageRange(pageRange, document.getNumberOfPages());
            List<String> extractedImages = new ArrayList<>();
            int imageCounter = 0;

            String timestamp = String.valueOf(System.currentTimeMillis());
            String baseFileName = getBaseFileName(filePath);

            for (Integer pageNum : pages) {
                PDPage page = document.getPage(pageNum - 1); // PDFBox uses 0-based indexing
                PDResources resources = page.getResources();

                if (resources != null) {
                    for (String name : resources.getXObjectNames()) {
                        PDXObject xObject = resources.getXObject(name);

                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject imageXObject = (PDImageXObject) xObject;
                            BufferedImage image = imageXObject.getImage();

                            String imageName = String.format("%s_page%d_img%d_%s.%s",
                                    baseFileName, pageNum, ++imageCounter, timestamp, imageFormat);
                            Path imagePath = Paths.get(outputDirectory, "images", imageName);

                            ImageIO.write(image, imageFormat, imagePath.toFile());
                            extractedImages.add(imagePath.toString());

                        } else if (xObject instanceof PDFormXObject) {
                            // Handle form XObjects that might contain images
                            extractImagesFromForm((PDFormXObject) xObject, baseFileName,
                                    pageNum, timestamp, imageFormat, extractedImages);
                        }
                    }
                }
            }

            return formatImageResult(extractedImages, pages.size(), outputFormat);

        } catch (Exception e) {
            throw new Exception("Failed to extract images from PDF: " + e.getMessage(), e);
        }
    }

    public String extractTables(String filePath, String outputFormat, String pageRange) throws Exception {
        validateFile(filePath);

        // Basic table extraction using text analysis
        // For more advanced table extraction, consider using libraries like Tabula or camelot-java

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            List<Integer> pages = parsePageRange(pageRange, document.getNumberOfPages());
            List<Map<String, Object>> tables = new ArrayList<>();

            PDFTextStripper stripper = new PDFTextStripper();

            for (Integer pageNum : pages) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String pageText = stripper.getText(document);

                // Simple table detection based on patterns
                List<Map<String, Object>> pageTables = detectTablesInText(pageText, pageNum);
                tables.addAll(pageTables);
            }

            // Save tables to file
            String timestamp = String.valueOf(System.currentTimeMillis());
            String baseFileName = getBaseFileName(filePath) + "_tables_" + timestamp;
            Path tablesFile = Paths.get(outputDirectory, "tables", baseFileName + ".json");

            ObjectNode tablesJson = objectMapper.createObjectNode();
            ArrayNode tablesArray = objectMapper.createArrayNode();
            for (Map<String, Object> table : tables) {
                tablesArray.add(objectMapper.valueToTree(table));
            }
            tablesJson.set("tables", tablesArray);

            Files.write(tablesFile, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tablesJson).getBytes());

            return formatTableResult(tables, tablesFile.toString(), outputFormat);

        } catch (Exception e) {
            throw new Exception("Failed to extract tables from PDF: " + e.getMessage(), e);
        }
    }

    public String extractFormFields(String filePath, String outputFormat) throws Exception {
        validateFile(filePath);

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            Map<String, Object> formData = new HashMap<>();

            if (acroForm != null) {
                List<PDField> fields = acroForm.getFields();

                for (PDField field : fields) {
                    String fieldName = field.getFullyQualifiedName();
                    String fieldType = field.getFieldType();
                    String fieldValue = field.getValueAsString();
                    boolean isReadOnly = field.isReadOnly();
                    boolean isRequired = field.isRequired();

                    Map<String, Object> fieldInfo = new HashMap<>();
                    fieldInfo.put("name", fieldName);
                    fieldInfo.put("type", fieldType);
                    fieldInfo.put("value", fieldValue);
                    fieldInfo.put("readonly", isReadOnly);
                    fieldInfo.put("required", isRequired);

                    formData.put(fieldName, fieldInfo);
                }
            }

            // Save form data to file
            String timestamp = String.valueOf(System.currentTimeMillis());
            String baseFileName = getBaseFileName(filePath) + "_forms_" + timestamp;
            Path formsFile = Paths.get(outputDirectory, "forms", baseFileName + ".json");

            Files.write(formsFile, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(formData).getBytes());

            return formatFormResult(formData, formsFile.toString(), outputFormat);

        } catch (Exception e) {
            throw new Exception("Failed to extract form fields from PDF: " + e.getMessage(), e);
        }
    }

    public String extractMetadata(String filePath, String outputFormat) throws Exception {
        validateFile(filePath);

        Map<String, Object> metadata = new HashMap<>();

        // Extract using PDFBox
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDDocumentInformation info = document.getDocumentInformation();

            metadata.put("title", info.getTitle());
            metadata.put("author", info.getAuthor());
            metadata.put("subject", info.getSubject());
            metadata.put("keywords", info.getKeywords());
            metadata.put("creator", info.getCreator());
            metadata.put("producer", info.getProducer());

            if (info.getCreationDate() != null) {
                metadata.put("creationDate", dateFormat.format(info.getCreationDate().getTime()));
            }
            if (info.getModificationDate() != null) {
                metadata.put("modificationDate", dateFormat.format(info.getModificationDate().getTime()));
            }

            metadata.put("pageCount", document.getNumberOfPages());
            metadata.put("encrypted", document.isEncrypted());
            metadata.put("version", document.getVersion());
        }

        // Extract using Tika for additional metadata
        try (InputStream input = new FileInputStream(filePath)) {
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata tikaMetadata = new Metadata();
            PDFParser parser = new PDFParser();

            parser.parse(input, handler, tikaMetadata, new org.apache.tika.parser.ParseContext());

            // Add Tika metadata
            String[] metadataNames = tikaMetadata.names();
            Map<String, String> tikaData = new HashMap<>();
            for (String name : metadataNames) {
                tikaData.put(name, tikaMetadata.get(name));
            }
            metadata.put("tikaMetadata", tikaData);
        }

        // Add file system metadata
        File file = new File(filePath);
        metadata.put("fileName", file.getName());
        metadata.put("filePath", file.getAbsolutePath());
        metadata.put("fileSize", file.length());
        metadata.put("lastModified", dateFormat.format(new Date(file.lastModified())));

        // Save metadata to file
        String timestamp = String.valueOf(System.currentTimeMillis());
        String baseFileName = getBaseFileName(filePath) + "_metadata_" + timestamp;
        Path metadataFile = Paths.get(outputDirectory, "metadata", baseFileName + ".json");

        Files.write(metadataFile, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(metadata).getBytes());

        return formatMetadataResult(metadata, metadataFile.toString(), outputFormat);
    }

    public String extractFull(String filePath, String outputFormat, String pageRange) throws Exception {
        validateFile(filePath);

        Map<String, Object> fullExtraction = new HashMap<>();

        // Extract all components
        String textResult = extractText(filePath, "json", pageRange);
        String imageResult = extractImages(filePath, "json", "png", pageRange);
        String tableResult = extractTables(filePath, "json", pageRange);
        String formResult = extractFormFields(filePath, "json");
        String metadataResult = extractMetadata(filePath, "json");

        // Parse JSON results
        fullExtraction.put("text", objectMapper.readValue(textResult, Map.class));
        fullExtraction.put("images", objectMapper.readValue(imageResult, Map.class));
        fullExtraction.put("tables", objectMapper.readValue(tableResult, Map.class));
        fullExtraction.put("forms", objectMapper.readValue(formResult, Map.class));
        fullExtraction.put("metadata", objectMapper.readValue(metadataResult, Map.class));

        fullExtraction.put("extractionTimestamp", dateFormat.format(new Date()));
        fullExtraction.put("sourceFile", filePath);

        // Save full extraction to file
        String timestamp = String.valueOf(System.currentTimeMillis());
        String baseFileName = getBaseFileName(filePath) + "_full_" + timestamp;
        Path fullFile = Paths.get(outputDirectory, baseFileName + ".json");

        Files.write(fullFile, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(fullExtraction).getBytes());

        return formatFullResult(fullExtraction, fullFile.toString(), outputFormat);
    }

    // Helper methods
    private void validateFile(String filePath) throws Exception {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new FileNotFoundException("PDF file not found: " + filePath);
        }

        if (!file.isFile()) {
            throw new IllegalArgumentException("Path is not a file: " + filePath);
        }

        if (file.length() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size: " +
                    (file.length() / 1024 / 1024) + "MB > " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".pdf")) {
            throw new IllegalArgumentException("File is not a PDF: " + filePath);
        }
    }

    private List<Integer> parsePageRange(String pageRange, int totalPages) throws Exception {
        List<Integer> pages = new ArrayList<>();

        if ("all".equalsIgnoreCase(pageRange.trim())) {
            for (int i = 1; i <= Math.min(totalPages, maxPages); i++) {
                pages.add(i);
            }
            return pages;
        }

        String[] parts = pageRange.split(",");
        for (String part : parts) {
            part = part.trim();

            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length != 2) {
                    throw new IllegalArgumentException("Invalid page range format: " + part);
                }

                int start = Integer.parseInt(range[0].trim());
                int end = Integer.parseInt(range[1].trim());

                if (start > end) {
                    throw new IllegalArgumentException("Invalid page range: start > end");
                }

                for (int i = start; i <= Math.min(end, totalPages); i++) {
                    if (i > 0 && !pages.contains(i)) {
                        pages.add(i);
                    }
                }
            } else {
                int pageNum = Integer.parseInt(part);
                if (pageNum > 0 && pageNum <= totalPages && !pages.contains(pageNum)) {
                    pages.add(pageNum);
                }
            }
        }

        Collections.sort(pages);
        return pages;
    }

    private String getBaseFileName(String filePath) {
        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    private void extractImagesFromForm(PDFormXObject form, String baseFileName,
                                       int pageNum, String timestamp, String imageFormat, List<String> extractedImages) {
        // This is a simplified implementation
        // In a full implementation, you would recursively process form XObjects
    }

    private List<Map<String, Object>> detectTablesInText(String text, int pageNum) {
        List<Map<String, Object>> tables = new ArrayList<>();

        // Simple table detection based on multiple tabs/spaces in a line
        String[] lines = text.split("\n");
        List<String> tableLines = new ArrayList<>();
        boolean inTable = false;

        Pattern tablePattern = Pattern.compile(".*\\t.*\\t.*"); // At least 2 tabs

        for (String line : lines) {
            if (tablePattern.matcher(line).matches()) {
                tableLines.add(line);
                inTable = true;
            } else if (inTable && !line.trim().isEmpty()) {
                // End of table
                if (!tableLines.isEmpty()) {
                    Map<String, Object> table = new HashMap<>();
                    table.put("page", pageNum);
                    table.put("rowCount", tableLines.size());
                    table.put("data", new ArrayList<>(tableLines));

                    // Try to extract headers (first row)
                    if (!tableLines.isEmpty()) {
                        String[] headers = tableLines.get(0).split("\t");
                        table.put("headers", Arrays.asList(headers));

                        // Extract rows
                        List<List<String>> rows = new ArrayList<>();
                        for (int i = 1; i < tableLines.size(); i++) {
                            String[] rowData = tableLines.get(i).split("\t");
                            rows.add(Arrays.asList(rowData));
                        }
                        table.put("rows", rows);
                    }

                    tables.add(table);
                }
                tableLines.clear();
                inTable = false;
            }
        }

        // Handle table at end of page
        if (!tableLines.isEmpty()) {
            Map<String, Object> table = new HashMap<>();
            table.put("page", pageNum);
            table.put("rowCount", tableLines.size());
            table.put("data", new ArrayList<>(tableLines));
            tables.add(table);
        }

        return tables;
    }

    // Formatting methods for different output formats
    private String formatTextResult(Map<Integer, String> pageTexts, String fullText,
                                    String filePath, String outputFormat) throws Exception {

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("extractionType", "text");
        result.put("pageCount", pageTexts.size());
        result.put("outputFile", filePath);
        result.put("timestamp", dateFormat.format(new Date()));

        switch (outputFormat.toLowerCase()) {
            case "json":
                result.put("pageTexts", pageTexts);
                result.put("fullText", fullText);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

            case "markdown":
                StringBuilder md = new StringBuilder();
                md.append("# PDF Text Extraction\n\n");
                md.append("**Status:** ").append(result.get("status")).append("\n");
                md.append("**Pages:** ").append(result.get("pageCount")).append("\n");
                md.append("**Timestamp:** ").append(result.get("timestamp")).append("\n\n");
                md.append("## Full Text\n\n");
                md.append("```\n").append(fullText).append("\n```\n");
                return md.toString();

            case "plaintext":
                return fullText;

            default:
                throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
        }
    }

    private String formatImageResult(List<String> images, int pageCount, String outputFormat) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("extractionType", "images");
        result.put("imageCount", images.size());
        result.put("pageCount", pageCount);
        result.put("images", images);
        result.put("timestamp", dateFormat.format(new Date()));

        switch (outputFormat.toLowerCase()) {
            case "json":
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

            case "markdown":
                StringBuilder md = new StringBuilder();
                md.append("# PDF Image Extraction\n\n");
                md.append("**Status:** ").append(result.get("status")).append("\n");
                md.append("**Images Found:** ").append(result.get("imageCount")).append("\n");
                md.append("**Pages Processed:** ").append(result.get("pageCount")).append("\n");
                md.append("**Timestamp:** ").append(result.get("timestamp")).append("\n\n");
                md.append("## Extracted Images\n\n");
                for (String imagePath : images) {
                    md.append("- ").append(imagePath).append("\n");
                }
                return md.toString();

            case "plaintext":
                StringBuilder txt = new StringBuilder();
                txt.append("PDF Image Extraction Results\n");
                txt.append("Images found: ").append(images.size()).append("\n");
                txt.append("Pages processed: ").append(pageCount).append("\n\n");
                txt.append("Image files:\n");
                for (String imagePath : images) {
                    txt.append(imagePath).append("\n");
                }
                return txt.toString();

            default:
                throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
        }
    }

    private String formatTableResult(List<Map<String, Object>> tables, String filePath, String outputFormat) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("extractionType", "tables");
        result.put("tableCount", tables.size());
        result.put("tables", tables);
        result.put("outputFile", filePath);
        result.put("timestamp", dateFormat.format(new Date()));

        switch (outputFormat.toLowerCase()) {
            case "json":
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

            case "markdown":
                StringBuilder md = new StringBuilder();
                md.append("# PDF Table Extraction\n\n");
                md.append("**Status:** ").append(result.get("status")).append("\n");
                md.append("**Tables Found:** ").append(result.get("tableCount")).append("\n");
                md.append("**Timestamp:** ").append(result.get("timestamp")).append("\n\n");

                for (int i = 0; i < tables.size(); i++) {
                    Map<String, Object> table = tables.get(i);
                    md.append("## Table ").append(i + 1).append(" (Page ").append(table.get("page")).append(")\n\n");

                    @SuppressWarnings("unchecked")
                    List<String> headers = (List<String>) table.get("headers");
                    @SuppressWarnings("unchecked")
                    List<List<String>> rows = (List<List<String>>) table.get("rows");

                    if (headers != null && !headers.isEmpty()) {
                        md.append("| ").append(String.join(" | ", headers)).append(" |\n");
                        md.append("|").append(" --- |".repeat(headers.size())).append("\n");

                        if (rows != null) {
                            for (List<String> row : rows) {
                                md.append("| ").append(String.join(" | ", row)).append(" |\n");
                            }
                        }
                    }
                    md.append("\n");
                }
                return md.toString();

            case "plaintext":
        }
        return filePath;
    }
}