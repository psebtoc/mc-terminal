package com.pyosechang.terminal.claude;

import com.pyosechang.terminal.terminal.TerminalConfig;
import com.pyosechang.terminal.terminal.TerminalSession;
import com.pyosechang.terminal.terminal.TerminalSessionManager;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Claude Code guide sessions running inside terminal tabs.
 *
 * A "guide session" = a terminal tab whose working directory is the Minecraft instance directory.
 * Messages are sent by writing directly to the PTY input stream.
 */
public class ClaudeGuideManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeGuideManager.class);
    private static final ClaudeGuideManager INSTANCE = new ClaudeGuideManager();

    // Maps Claude Code session_id → terminal tab name
    private final Map<String, String> sessionToTabName = new ConcurrentHashMap<>();

    private ClaudeGuideManager() {}

    public static ClaudeGuideManager getInstance() {
        return INSTANCE;
    }

    /**
     * Find all terminal sessions whose start directory matches the game directory.
     */
    public List<TerminalSession> findGuideSessions() {
        List<TerminalSession> guideSessions = new ArrayList<>();
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();

        for (TerminalSession session : TerminalSessionManager.getSessions()) {
            if (!session.isAlive()) continue;

            String sessionDir = session.getStartDirectory();
            if (sessionDir != null) {
                Path sessionPath = Path.of(sessionDir).toAbsolutePath().normalize();
                if (sessionPath.equals(gameDir)) {
                    guideSessions.add(session);
                }
            }
        }
        return guideSessions;
    }

    /**
     * Find a guide session by tab name.
     */
    public TerminalSession findGuideSessionByName(String tabName) {
        for (TerminalSession session : findGuideSessions()) {
            if (tabName.equals(session.getName())) {
                return session;
            }
        }
        return null;
    }

    /**
     * Send a message to a terminal session by writing to its PTY input.
     */
    public void sendMessage(TerminalSession session, String message) {
        if (session == null || !session.isAlive()) {
            throw new IllegalStateException("Session is not alive");
        }
        // Write message + Enter to PTY
        session.write(message + "\r");
        LOGGER.info("Sent message to tab '{}': {}", session.getName(), message);
    }

    /**
     * Check if Claude CLI is available on PATH.
     */
    /**
     * Check if Claude CLI is available.
     */
    public boolean isClaudeAvailable() {
        String cliPath = getClaudeCliPath();
        try {
            ProcessBuilder pb = new ProcessBuilder(cliPath, "--version");
            pb.environment().remove("CLAUDECODE");
            pb.environment().remove("CLAUDE_CODE_SESSION");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            LOGGER.warn("Claude CLI not found at '{}': {}", cliPath, e.getMessage());
            return false;
        }
    }

    /**
     * Register a Claude Code session_id to a tab name (called from SessionStart hook).
     */
    public void registerSession(String sessionId, String tabName) {
        sessionToTabName.put(sessionId, tabName);
        LOGGER.info("Registered session {} -> tab '{}'", sessionId, tabName);
    }

    /**
     * Get the tab name for a Claude Code session_id.
     */
    public String getTabNameForSession(String sessionId) {
        return sessionToTabName.get(sessionId);
    }

    private String getClaudeCliPath() {
        String configured = TerminalConfig.getClaudeCliPath();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return "claude";
    }
}
