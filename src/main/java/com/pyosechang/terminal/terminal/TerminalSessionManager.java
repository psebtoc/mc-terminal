package com.pyosechang.terminal.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the singleton terminal session.
 * Session persists when the terminal screen is closed.
 */
public class TerminalSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSessionManager.class);

    private static final int DEFAULT_COLUMNS = 120;
    private static final int DEFAULT_ROWS = 30;

    private static TerminalSession currentSession;

    public static TerminalSession getOrCreate() {
        if (currentSession == null || !currentSession.isAlive()) {
            currentSession = new TerminalSession(DEFAULT_COLUMNS, DEFAULT_ROWS);
            try {
                currentSession.start();
            } catch (Exception e) {
                LOGGER.error("Failed to start terminal session", e);
                currentSession = null;
            }
        }
        return currentSession;
    }

    public static void destroyCurrent() {
        if (currentSession != null) {
            currentSession.destroy();
            currentSession = null;
        }
    }

    public static TerminalSession getCurrent() {
        return currentSession;
    }
}
