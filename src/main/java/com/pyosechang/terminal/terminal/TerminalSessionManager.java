package com.pyosechang.terminal.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-tab terminal session manager.
 */
public class TerminalSessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSessionManager.class);

    private static final List<TerminalSession> sessions = new ArrayList<>();
    private static int activeIndex = -1;

    public static TerminalSession getOrCreate() {
        if (sessions.isEmpty() || activeIndex < 0) {
            return createNewTab(120, 30);
        }
        TerminalSession session = sessions.get(activeIndex);
        if (!session.isAlive()) {
            sessions.remove(activeIndex);
            if (sessions.isEmpty()) {
                return createNewTab(120, 30);
            }
            activeIndex = Math.min(activeIndex, sessions.size() - 1);
            return sessions.get(activeIndex);
        }
        return session;
    }

    private static int tabCounter = 0;

    public static TerminalSession createNewTab(int cols, int rows) {
        TerminalSession session = new TerminalSession(cols, rows);
        try {
            session.start();
            tabCounter++;
            session.setName("Terminal " + tabCounter);
            sessions.add(session);
            activeIndex = sessions.size() - 1;
            return session;
        } catch (Exception e) {
            LOGGER.error("Failed to start terminal session", e);
            return null;
        }
    }

    public static void closeTab(int index) {
        if (index >= 0 && index < sessions.size()) {
            sessions.get(index).destroy();
            sessions.remove(index);
            if (sessions.isEmpty()) {
                activeIndex = -1;
            } else {
                activeIndex = Math.min(activeIndex, sessions.size() - 1);
            }
        }
    }

    public static void closeActiveTab() {
        if (activeIndex >= 0) {
            closeTab(activeIndex);
        }
    }

    public static void switchTo(int index) {
        if (index >= 0 && index < sessions.size()) {
            activeIndex = index;
        }
    }

    public static void nextTab() {
        if (sessions.size() > 1) {
            activeIndex = (activeIndex + 1) % sessions.size();
        }
    }

    public static void prevTab() {
        if (sessions.size() > 1) {
            activeIndex = (activeIndex - 1 + sessions.size()) % sessions.size();
        }
    }

    public static int getActiveIndex() { return activeIndex; }
    public static int getTabCount() { return sessions.size(); }
    public static List<TerminalSession> getSessions() { return sessions; }

    public static TerminalSession getActive() {
        if (activeIndex >= 0 && activeIndex < sessions.size()) {
            return sessions.get(activeIndex);
        }
        return null;
    }

    public static void destroyAll() {
        for (TerminalSession s : sessions) s.destroy();
        sessions.clear();
        activeIndex = -1;
    }
}
