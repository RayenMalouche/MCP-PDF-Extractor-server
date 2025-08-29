package com.mcp.RayenMalouche.pdf.PDFExtractor.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find application.properties");
            } else {
                properties.load(input);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.err.println("Invalid integer value for property: " + key);
            }
        }
        return defaultValue;
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    public static double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                System.err.println("Invalid double value for property: " + key);
            }
        }
        return defaultValue;
    }

    public static long parseSizeProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                value = value.toUpperCase().trim();
                if (value.endsWith("MB")) {
                    return Long.parseLong(value.substring(0, value.length() - 2)) * 1024 * 1024;
                } else if (value.endsWith("KB")) {
                    return Long.parseLong(value.substring(0, value.length() - 2)) * 1024;
                } else if (value.endsWith("GB")) {
                    return Long.parseLong(value.substring(0, value.length() - 2)) * 1024 * 1024 * 1024;
                } else {
                    return Long.parseLong(value);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid size value for property: " + key);
            }
        }
        return defaultValue;
    }
}
