package com.pyosechang.terminal.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalSession.class);

    private final int columns;
    private final int rows;
    private String name;

    private PtyProcess process;
    private JediTerminal terminal;
    private TerminalTextBuffer textBuffer;
    private StyleState styleState;
    private JediEmulator emulator;

    private OutputStream outputStream;
    private Thread readerThread;
    private final AtomicBoolean alive = new AtomicBoolean(false);

    public TerminalSession(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        this.name = null; // set externally
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public void start() throws IOException {
        String[] cmd;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Use cmd.exe with UTF-8 codepage for proper Korean/Unicode support
            cmd = new String[]{"cmd.exe", "/K", "chcp 65001 >nul"};
        } else {
            String shell = System.getenv("SHELL");
            if (shell == null) shell = "/bin/bash";
            cmd = new String[]{shell, "-l"};
        }

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");

        // Remove Claude Code env vars so claude can be launched from this terminal
        env.remove("CLAUDECODE");
        env.remove("CLAUDE_CODE_SESSION");

        process = new PtyProcessBuilder()
                .setCommand(cmd)
                .setEnvironment(env)
                .setInitialColumns(columns)
                .setInitialRows(rows)
                .setConsole(false)
                .start();

        outputStream = process.getOutputStream();

        styleState = new StyleState();
        styleState.setDefaultStyle(new TextStyle(TerminalColor.WHITE, TerminalColor.BLACK));

        textBuffer = new TerminalTextBuffer(columns, rows, styleState);

        terminal = new JediTerminal(new TerminalDisplayBridge(), textBuffer, styleState);

        InputStreamDataStream dataStream = new InputStreamDataStream(process.getInputStream());
        emulator = new JediEmulator(dataStream, terminal);

        alive.set(true);

        readerThread = new Thread(this::readLoop, "terminal-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try {
            while (alive.get() && process.isAlive()) {
                if (emulator.hasNext()) {
                    emulator.next();
                }
            }
        } catch (Exception e) {
            if (alive.get()) {
                LOGGER.warn("Terminal reader ended: {}", e.getMessage());
            }
        } finally {
            alive.set(false);
        }
    }

    public void write(byte[] data) {
        if (!alive.get()) return;
        try {
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to write to terminal: {}", e.getMessage());
        }
    }

    public void write(String text) {
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    public void resize(int newColumns, int newRows) {
        if (process != null && process.isAlive()) {
            try {
                process.setWinSize(new WinSize(newColumns, newRows));
                terminal.resize(new TermSize(newColumns, newRows), RequestOrigin.User);
            } catch (Exception e) {
                LOGGER.warn("Failed to resize terminal: {}", e.getMessage());
            }
        }
    }

    public TerminalTextBuffer getTextBuffer() {
        return textBuffer;
    }

    public StyleState getStyleState() {
        return styleState;
    }

    public JediTerminal getTerminal() {
        return terminal;
    }

    public boolean isAlive() {
        return alive.get() && process != null && process.isAlive();
    }

    public void destroy() {
        alive.set(false);
        if (readerThread != null) {
            readerThread.interrupt();
        }
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
