package io.classroomlan.server.handlers;

import com.sun.net.httpserver.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 静态资源处理器
 * CONTEXT.txt: GET / 和 /assets/*
 */
public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        // 路径转换为资源路径：/index.html -> static/index.html
        String resourcePath = "static" + path;

        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is != null) {
            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);

            byte[] content = is.readAllBytes();
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();
        } else {
            String response = "404 Not Found";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private String getContentType(String path) {
        if (path.endsWith(".html") || path.equals("/")) return "text/html; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/plain; charset=UTF-8";
    }
}