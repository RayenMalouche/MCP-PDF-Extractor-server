package com.mcp.RayenMalouche.pdf.PDFExtractor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.RayenMalouche.pdf.PDFExtractor.Service.TikaExtractorService;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * TestServlet for testing extraction functionality via REST API
 */
public class TestServlet extends HttpServlet {

    private final TikaExtractorService extractorService;
    private final ObjectMapper mapper;

    public TestServlet(TikaExtractorService extractorService) {
        this.extractorService = extractorService;
        this.mapper = new ObjectMapper();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        addCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        if ("/list".equals(pathInfo)) {
            handleListFiles(resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"status\": \"error\", \"message\": \"Unknown endpoint\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        addCorsHeaders(resp);

        String pathInfo = req.getPathInfo();

        try {
            if ("/extract-html".equals(pathInfo)) {
                resp.setContentType("application/json");
                handleExtractHtml(req, resp);
            } else if ("/extract-text".equals(pathInfo)) {
                resp.setContentType("application/json");
                handleExtractText(req, resp);
            } else if ("/raw-html".equals(pathInfo)) {
                resp.setContentType("text/html");  // New: Set to text/html for raw HTML
                handleRawHtml(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"status\": \"error\", \"message\": \"Unknown endpoint\"}");
            }
        } catch (Exception e) {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(String.format("{\"status\": \"error\", \"message\": \"%s\"}", escapeJson(e.getMessage())));
        }
    }

    private void handleListFiles(HttpServletResponse resp) throws IOException {
        Map<String, Object> result = extractorService.listAvailableFiles();
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(result));
    }

    private void handleExtractHtml(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> requestBody = parseRequestBody(req);
        String filename = (String) requestBody.get("filename");

        if (filename == null || filename.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"status\": \"error\", \"message\": \"Filename is required\"}");
            return;
        }

        Map<String, Object> result = extractorService.extractToHtml(filename);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(result));
    }

    private void handleExtractText(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> requestBody = parseRequestBody(req);
        String filename = (String) requestBody.get("filename");

        if (filename == null || filename.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"status\": \"error\", \"message\": \"Filename is required\"}");
            return;
        }

        Map<String, Object> result = extractorService.extractText(filename);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(result));
    }

    // New handler for raw HTML output
    private void handleRawHtml(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Map<String, Object> requestBody = parseRequestBody(req);
        String filename = (String) requestBody.get("filename");

        if (filename == null || filename.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("<html><body><h1>Error: Filename is required</h1></body></html>");
            return;
        }

        Map<String, Object> result = extractorService.extractToHtml(filename);
        String html = (String) result.get("html");

        if (html == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("<html><body><h1>Error: Failed to extract HTML</h1></body></html>");
            return;
        }

        // Directly write the HTML (no JSON)
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(html);
    }

    private Map<String, Object> parseRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }
        return mapper.readValue(requestBody.toString(), Map.class);
    }

    private void addCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        addCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}