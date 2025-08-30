package com.mcp.RayenMalouche.pdf.PDFExtractor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HealthServlet for checking the server's status
 */
public class HealthServlet extends HttpServlet {

    private final ObjectMapper mapper;

    public HealthServlet() {
        this.mapper = new ObjectMapper();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        addCorsHeaders(resp);

        Map<String, Object> status = new HashMap<>();
        status.put("status", "ok");
        status.put("server", "Tika MCP Extractor Server");
        status.put("version", "1.0.0");

        // Check if files-to-extract directory is accessible
        File directory = new File("files-to-extract");
        status.put("filesDirectoryExists", directory.exists());
        status.put("filesDirectoryReadable", directory.canRead());
        status.put("filesDirectoryWritable", directory.canWrite());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(status));
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        addCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void addCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}