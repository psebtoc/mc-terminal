package com.pyosechang.terminal.claude;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HookSetup {

    private static final Logger LOGGER = LoggerFactory.getLogger(HookSetup.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setup() throws IOException {
        createHookScript();
        registerHooksInSettings();
        createClaudeMd();
    }

    private static void createHookScript() throws IOException {
        Path hookDir = FMLPaths.GAMEDIR.get().resolve(".terminal-mod/hooks");
        Files.createDirectories(hookDir);

        Path hookScript = hookDir.resolve("guide-hook.mjs");

        String script = """
                // Terminal Mod - Claude Guide Hook Script
                // Reads port from hook-server.json and sends hook events to the in-game hook server

                import { readFileSync } from 'fs';
                import { dirname, join } from 'path';
                import { fileURLToPath } from 'url';

                // Use script location (not CWD) to find port file reliably
                // Script lives at <gameDir>/.terminal-mod/hooks/guide-hook.mjs
                const __dirname = dirname(fileURLToPath(import.meta.url));

                async function main() {
                    let port;
                    try {
                        const portFile = join(__dirname, '..', 'hook-server.json');
                        const data = JSON.parse(readFileSync(portFile, 'utf-8'));
                        port = data.port;
                    } catch {
                        // No port file = game not running, silently exit
                        process.exit(0);
                    }

                    // Read stdin for hook payload
                    let stdinData = '';
                    try {
                        stdinData = readFileSync(0, 'utf-8');
                    } catch {
                        // No stdin data
                    }

                    let payload = {};
                    try {
                        payload = JSON.parse(stdinData);
                    } catch {
                        payload = {};
                    }

                    try {
                        const response = await fetch(`http://localhost:${port}/hook`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(payload),
                        });
                        // Ignore non-200 silently — game may be shutting down
                    } catch {
                        // Server not running, silently exit
                    }
                }

                main().catch(() => process.exit(0));
                """;

        Files.writeString(hookScript, script, StandardCharsets.UTF_8);
        LOGGER.info("Hook script created: {}", hookScript);
    }

    private static void registerHooksInSettings() throws IOException {
        Path settingsPath = getClaudeSettingsPath();
        JsonObject settings;

        if (Files.exists(settingsPath)) {
            String content = Files.readString(settingsPath, StandardCharsets.UTF_8);
            try {
                settings = GSON.fromJson(content, JsonObject.class);
            } catch (JsonSyntaxException e) {
                LOGGER.warn("Invalid settings.json, creating new one");
                settings = new JsonObject();
            }
        } else {
            Files.createDirectories(settingsPath.getParent());
            settings = new JsonObject();
        }

        // Ensure hooks object exists
        if (!settings.has("hooks") || !settings.get("hooks").isJsonObject()) {
            settings.add("hooks", new JsonObject());
        }
        JsonObject hooks = settings.getAsJsonObject("hooks");

        Path hookScript = FMLPaths.GAMEDIR.get().resolve(".terminal-mod/hooks/guide-hook.mjs");
        String hookCommand = "node \"" + hookScript.toAbsolutePath().toString().replace("\\", "/") + "\"";

        // Register Stop hook
        registerHook(hooks, "Stop", hookCommand);

        // Register SessionStart hook
        registerHook(hooks, "SessionStart", hookCommand);

        Files.writeString(settingsPath, GSON.toJson(settings), StandardCharsets.UTF_8);
        LOGGER.info("Hooks registered in {}", settingsPath);
    }

    private static void registerHook(JsonObject hooks, String hookName, String hookCommand) {
        JsonArray hookArray;
        if (hooks.has(hookName) && hooks.get(hookName).isJsonArray()) {
            hookArray = hooks.getAsJsonArray(hookName);
        } else {
            hookArray = new JsonArray();
            hooks.add(hookName, hookArray);
        }

        // Remove any existing guide-hook entries (old or new format)
        String searchStr = "guide-hook.mjs";
        for (int i = hookArray.size() - 1; i >= 0; i--) {
            JsonElement elem = hookArray.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject entry = elem.getAsJsonObject();

            // Old format: {"type":"command", "command":"...guide-hook.mjs"}
            if (entry.has("command") && entry.get("command").getAsString().contains(searchStr)) {
                hookArray.remove(i);
                continue;
            }
            // New format: {"matcher":{}, "hooks":[{"command":"...guide-hook.mjs"}]}
            if (entry.has("hooks") && entry.get("hooks").isJsonArray()) {
                JsonArray innerHooks = entry.getAsJsonArray("hooks");
                for (int j = 0; j < innerHooks.size(); j++) {
                    JsonObject hook = innerHooks.get(j).getAsJsonObject();
                    if (hook.has("command") && hook.get("command").getAsString().contains(searchStr)) {
                        hookArray.remove(i);
                        break;
                    }
                }
            }
        }

        // Add new entry with matcher + hooks format
        JsonObject hookObj = new JsonObject();
        hookObj.addProperty("type", "command");
        hookObj.addProperty("command", hookCommand);

        JsonArray innerHooks = new JsonArray();
        innerHooks.add(hookObj);

        JsonObject entry = new JsonObject();
        entry.addProperty("matcher", "");
        entry.add("hooks", innerHooks);
        hookArray.add(entry);
    }

    private static void createClaudeMd() throws IOException {
        Path claudeMd = FMLPaths.GAMEDIR.get().resolve("CLAUDE.md");

        // Load template from resources
        String template;
        try (InputStream is = HookSetup.class.getResourceAsStream("/assets/terminal/claude_guide_prompt.md")) {
            if (is != null) {
                template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                LOGGER.warn("claude_guide_prompt.md not found in resources, using default");
                template = getDefaultPrompt();
            }
        }

        Files.writeString(claudeMd, template, StandardCharsets.UTF_8);
        LOGGER.info("CLAUDE.md created: {}", claudeMd);
    }

    private static String getDefaultPrompt() {
        return """
                # Minecraft Guide Mode

                You are a helpful Minecraft guide assistant running inside the game.

                ## Rules
                - Answer in the player's language
                - Keep responses concise (under 5 lines when possible)
                - Focus on practical, actionable advice
                - Use item/block names the player would recognize
                - If asked about crafting, describe the recipe layout
                - Do NOT use tools or execute commands — you are in print mode
                """;
    }

    /**
     * Project-local settings: <gamedir>/.claude/settings.json
     */
    private static Path getClaudeSettingsPath() {
        return FMLPaths.GAMEDIR.get().resolve(".claude").resolve("settings.json");
    }

}
