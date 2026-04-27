package io.classroomlan.server.handlers;

import com.sun.net.httpserver.*;
import io.classroomlan.node.NodeState;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

/**
 * 文件 API 处理器
 */
public class ApiFileHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ApiFileHandler.class.getName());
    private static final Map<String, FileInfo> files = new HashMap<>();
    private static Path uploadDir;

    private final NodeState nodeState;

    public ApiFileHandler(NodeState nodeState) {
        this.nodeState = nodeState;
        if (uploadDir == null) {
            uploadDir = Paths.get("uploads");
            try {
                Files.createDirectories(uploadDir);
                LOGGER.info("Upload directory: " + uploadDir.toAbsolutePath());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create upload dir", e);
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.startsWith("/api/files") && "GET".equals(method)) {
                handleListFiles(exchange);
            } else if (path.startsWith("/api/upload") && "POST".equals(method)) {
                handleUpload(exchange);
            } else if (path.startsWith("/api/download")) {
                handleDownload(exchange);
            } else if (path.startsWith("/api/peers")) {
                handleListPeers(exchange);
            } else {
                sendResponse(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Handler error", e);
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    private void handleListFiles(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (FileInfo info : files.values()) {
            if (!first) sb.append(",");
            sb.append(String.format("{\"id\":\"%s\",\"name\":\"%s\",\"size\":%d,\"uploadedBy\":\"%s\"}",
                escapeJson(info.id), escapeJson(info.name), info.size, escapeJson(info.uploader)));
            first = false;
        }
        sb.append("]");
        sendJson(exchange, sb.toString());
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        byte[] rawData = exchange.getRequestBody().readAllBytes();

        if (rawData.length == 0) {
            sendJson(exchange, "{\"error\":\"No file data\"}");
            return;
        }

        String fileName = "file_" + System.currentTimeMillis();
        byte[] fileContent = rawData;

        if (contentType != null && contentType.contains("multipart/form-data")) {
            // 直接用 UTF-8 解析（现代浏览器发送 UTF-8 编码的文件名）
            String bodyStr = new String(rawData, StandardCharsets.UTF_8);
            if (bodyStr.contains("filename=\"")) {
                int fnStart = bodyStr.indexOf("filename=\"") + 10;
                int fnEnd = bodyStr.indexOf("\"", fnStart);
                if (fnEnd > fnStart) fileName = bodyStr.substring(fnStart, fnEnd);
            }

            // 提取文件内容
            int contentStart = bodyStr.indexOf("\r\n\r\n");
            if (contentStart == -1) contentStart = bodyStr.indexOf("\n\n");
            if (contentStart != -1) {
                contentStart += 4;
                int boundaryEnd = bodyStr.indexOf("\r\n--", contentStart);
                if (boundaryEnd == -1) boundaryEnd = bodyStr.indexOf("\n--", contentStart);
                if (boundaryEnd == -1) boundaryEnd = rawData.length;
                else boundaryEnd -= 2;

                int actualLen = Math.max(0, boundaryEnd - contentStart);
                if (actualLen > 0 && actualLen < rawData.length) {
                    fileContent = new byte[actualLen];
                    System.arraycopy(rawData, contentStart, fileContent, 0, actualLen);
                }
            }
        }

        fileName = sanitizeFileName(fileName);
        String id = UUID.randomUUID().toString();
        Path filePath = uploadDir.resolve(id + "_" + fileName);

        try {
            Files.write(filePath, fileContent);
            long size = Files.size(filePath);

            FileInfo info = new FileInfo(id, fileName, size, "user");
            files.put(id, info);

            LOGGER.info("Uploaded: " + fileName + " (" + size + " bytes)");
            sendJson(exchange, "{\"id\":\"" + id + "\",\"name\":\"" + escapeJson(fileName) + "\",\"size\":" + size + "}");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save file", e);
            sendJson(exchange, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String id = path.substring("/api/download/".length());

        FileInfo info = files.get(id);
        if (info == null) {
            sendResponse(exchange, 404, "File not found");
            return;
        }

        Path filePath = uploadDir.resolve(id + "_" + info.name);
        if (!Files.exists(filePath)) {
            sendResponse(exchange, 404, "File not found on disk");
            return;
        }

        // 使用 RFC 5987 编码文件名
        String encodedName = info.name.replace("%", "%25");
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + info.name + "\"; filename*=UTF-8''" + encodedName);

        byte[] data = Files.readAllBytes(filePath);
        exchange.sendResponseHeaders(200, data.length);
        OutputStream os = exchange.getResponseBody();
        os.write(data);
        os.close();
    }

    private void handleListPeers(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String peerId : nodeState.getPeerIds()) {
            if (!first) sb.append(",");
            sb.append(String.format("{\"nodeId\":\"%s\"}", escapeJson(peerId)));
            first = false;
        }
        sb.append("]");
        sendJson(exchange, sb.toString());
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        name = name.replaceAll("[/\\\\:*?\"<>|]", "_");
        if (name.length() > 100) name = name.substring(0, 100);
        return name;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private void sendResponse(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static class FileInfo {
        String id, name;
        long size;
        String uploader;

        FileInfo(String id, String name, long size, String uploader) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.uploader = uploader;
        }
    }
}