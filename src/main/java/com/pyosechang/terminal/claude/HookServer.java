package com.pyosechang.terminal.claude;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pyosechang.terminal.terminal.TerminalSession;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class HookServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HookServer.class);
    private static final Gson GSON = new Gson();
    private static final HookServer INSTANCE = new HookServer();

    private HttpServer server;
    private int port;
    private volatile boolean running = false;

    private HookServer() {}

    public static HookServer getInstance() {
        return INSTANCE;
    }

    public void start() throws IOException {
        if (running) {
            LOGGER.info("Hook server already running on port {}", port);
            return;
        }

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/hook", this::handleHook);
        server.setExecutor(null); // default executor
        server.start();
        running = true;

        writePortFile();
        LOGGER.info("Hook server started on port {}", port);
    }

    private void handleHook(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String body;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                body = reader.lines().collect(Collectors.joining("\n"));
            }

            LOGGER.info("Hook received: {}", body);

            JsonObject payload = GSON.fromJson(body, JsonObject.class);
            processHookPayload(payload);

            sendResponse(exchange, 200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            LOGGER.error("Hook processing error", e);
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void processHookPayload(JsonObject payload) {
        if (payload == null) return;

        String hookName = payload.has("hook_event_name") ? payload.get("hook_event_name").getAsString() : "";

        switch (hookName) {
            case "Stop":
                handleStopHook(payload);
                break;
            case "SessionStart":
                handleSessionStartHook(payload);
                break;
            default:
                LOGGER.debug("Unhandled hook: {}", hookName);
        }
    }

    private void handleStopHook(JsonObject payload) {
        if (!payload.has("last_assistant_message")) return;

        String message = payload.get("last_assistant_message").getAsString();
        String sessionId = payload.has("session_id") ? payload.get("session_id").getAsString() : "";

        // Look up tab name from session_id mapping
        String sessionName = ClaudeGuideManager.getInstance().getTabNameForSession(sessionId);
        if (sessionName == null) {
            // Fallback: single session → use its name, else "Claude"
            List<TerminalSession> guideSessions = ClaudeGuideManager.getInstance().findGuideSessions();
            sessionName = guideSessions.size() == 1 ? guideSessions.get(0).getName() : "Claude";
        }

        String finalName = sessionName;
        Minecraft.getInstance().execute(() -> {
            ClaudeGuideCommand.sendClaudeResponse(finalName, message);
        });
    }

    private void handleSessionStartHook(JsonObject payload) {
        String sessionId = payload.has("session_id") ? payload.get("session_id").getAsString() : "";
        if (sessionId.isEmpty()) return;

        // The most recently created guide session is the one that just started
        List<TerminalSession> guideSessions = ClaudeGuideManager.getInstance().findGuideSessions();
        if (!guideSessions.isEmpty()) {
            TerminalSession latest = guideSessions.get(guideSessions.size() - 1);
            ClaudeGuideManager.getInstance().registerSession(sessionId, latest.getName());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void writePortFile() throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve(".terminal-mod");
        Files.createDirectories(dir);

        Path portFile = dir.resolve("hook-server.json");
        JsonObject data = new JsonObject();
        data.addProperty("port", port);
        data.addProperty("pid", ProcessHandle.current().pid());

        Files.writeString(portFile, GSON.toJson(data), StandardCharsets.UTF_8);
        LOGGER.debug("Port file written: {}", portFile);
    }

    public void stop() {
        if (!running) return;

        if (server != null) {
            server.stop(0);
            server = null;
        }
        running = false;

        // Clean up port file
        try {
            Path portFile = FMLPaths.GAMEDIR.get().resolve(".terminal-mod/hook-server.json");
            Files.deleteIfExists(portFile);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete port file", e);
        }

        LOGGER.info("Hook server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
