package com.pyosechang.terminal.terminal;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Terminal configuration using Forge's config system.
 *
 * - CLIENT config (config/terminal-client.toml): global default directory
 * - SERVER config (<world>/serverconfig/terminal-server.toml): per-world override
 *
 * Users can edit per-world settings in the world's serverconfig folder,
 * or via the Mods menu config screen.
 */
public class TerminalConfig {

    // --- CLIENT config (global) ---
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_DIR;
    public static final ForgeConfigSpec.IntValue GUI_SCALE;
    public static final ForgeConfigSpec.ConfigValue<String> CLAUDE_CLI_PATH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Terminal global settings").push("general");
        DEFAULT_DIR = builder
                .comment("Default start directory for new terminal tabs")
                .define("defaultDir", System.getProperty("user.home"));
        GUI_SCALE = builder
                .comment("Terminal GUI scale override (0 = follow Minecraft setting, 1-4 = force specific scale)")
                .defineInRange("guiScale", 0, 0, 4);
        CLAUDE_CLI_PATH = builder
                .comment("Path to Claude CLI executable (empty = find 'claude' in PATH)")
                .define("claudeCliPath", "");
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    // --- SERVER config (per-world) ---
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> START_DIR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Terminal per-world settings").push("general");
        START_DIR = builder
                .comment("Start directory for this world (empty = use global default)")
                .define("startDir", "");
        builder.pop();
        SERVER_SPEC = builder.build();
    }

    /**
     * Get the configured Claude CLI path (empty = use PATH).
     */
    public static String getClaudeCliPath() {
        if (CLIENT_SPEC.isLoaded()) {
            return CLAUDE_CLI_PATH.get();
        }
        return "";
    }

    /**
     * Get the effective start directory: per-world override > global default.
     */
    public static String getStartDirectory() {
        // Per-world override
        if (SERVER_SPEC.isLoaded()) {
            String worldDir = START_DIR.get();
            if (worldDir != null && !worldDir.isEmpty()) {
                return worldDir;
            }
        }
        // Global default
        if (CLIENT_SPEC.isLoaded()) {
            return DEFAULT_DIR.get();
        }
        return System.getProperty("user.home");
    }
}
