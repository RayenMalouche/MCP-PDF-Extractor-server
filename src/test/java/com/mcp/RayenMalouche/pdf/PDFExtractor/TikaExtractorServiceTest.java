package com.mcp.RayenMalouche.pdf.PDFExtractor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.mcp.RayenMalouche.pdf.PDFExtractor.Service.TikaExtractorService;

import java.util.Map;

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