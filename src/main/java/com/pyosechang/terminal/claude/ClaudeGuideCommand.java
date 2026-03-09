package com.pyosechang.terminal.claude;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pyosechang.terminal.terminal.TerminalSession;
import com.pyosechang.terminal.terminal.TerminalSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.List;
import java.util.stream.Collectors;

public class ClaudeGuideCommand {

    private static final Style CLAUDE_PREFIX_STYLE = Style.EMPTY.withColor(0xFFAA00); // Gold
    private static final Style CLAUDE_BODY_STYLE = Style.EMPTY.withColor(0xFFFFFF);   // White
    private static final Style ERROR_STYLE = Style.EMPTY.withColor(0xFF5555);          // Red
    private static final Style SUCCESS_STYLE = Style.EMPTY.withColor(0x55FF55);        // Green
    private static final int MAX_CHAT_LINE_LENGTH = 250;

    private static int guideCounter = 0;

    /**
     * Suggests @tabname for guide sessions when input starts with @
     */
    private static final SuggestionProvider<CommandSourceStack> MESSAGE_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining();
        if (remaining.startsWith("@") || remaining.isEmpty()) {
            List<String> suggestions = ClaudeGuideManager.getInstance().findGuideSessions().stream()
                    .map(s -> "@" + s.getName() + " ")
                    .collect(Collectors.toList());
            for (String s : suggestions) {
                if (s.toLowerCase().startsWith(remaining.toLowerCase())) {
                    builder.suggest(s);
                }
            }
        }
        return builder.buildFuture();
    };

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("claude")
                .then(Commands.literal("onboarding")
                    .executes(ClaudeGuideCommand::onboarding))
                .then(Commands.literal("open")
                    .executes(ClaudeGuideCommand::open))
                .then(Commands.literal("status")
                    .executes(ClaudeGuideCommand::status))
                .then(Commands.literal("stop")
                    .executes(ClaudeGuideCommand::stop))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .suggests(MESSAGE_SUGGESTIONS)
                    .executes(ClaudeGuideCommand::ask))
        );
    }

    private static int onboarding(CommandContext<CommandSourceStack> ctx) {
        sendClaudeMessage("Setting up Claude Guide...");

        ClaudeGuideManager manager = ClaudeGuideManager.getInstance();

        if (!manager.isClaudeAvailable()) {
            sendError("Claude CLI not found. Install Claude Code first.");
            return 0;
        }

        try {
            HookSetup.setup();
            sendSuccess("Onboarding complete! Hook and CLAUDE.md registered.");
        } catch (Exception e) {
            sendError("Onboarding failed: " + e.getMessage());
            return 0;
        }

        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> ctx) {
        // Start hook server if not running
        try {
            HookServer.getInstance().start();
        } catch (Exception e) {
            sendError("Failed to start hook server: " + e.getMessage());
            return 0;
        }

        String gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().toString();
        TerminalSession session = TerminalSessionManager.createNewTab(120, 30, gameDir);

        if (session == null) {
            sendError("Failed to create terminal tab.");
            return 0;
        }

        // Name guide sessions without spaces: Guide, Guide2, Guide3...
        guideCounter++;
        String guideName = guideCounter == 1 ? "Guide" : "Guide" + guideCounter;
        session.setName(guideName);

        // Auto-run claude in the new tab
        session.write("claude\r");
        sendSuccess("Guide session opened in [" + guideName + "]");
        return 1;
    }

    private static int ask(CommandContext<CommandSourceStack> ctx) {
        String input = StringArgumentType.getString(ctx, "message");
        ClaudeGuideManager manager = ClaudeGuideManager.getInstance();

        // Parse @tabname prefix
        String tabName = null;
        String message = input;
        if (input.startsWith("@")) {
            int spaceIdx = input.indexOf(' ');
            if (spaceIdx > 0) {
                tabName = input.substring(1, spaceIdx);
                message = input.substring(spaceIdx + 1).trim();
            } else {
                sendError("Usage: /claude @<tabname> <message>");
                return 0;
            }
        }

        // Find target session
        TerminalSession target;
        if (tabName != null) {
            target = manager.findGuideSessionByName(tabName);
            if (target == null) {
                sendError("No guide session found: " + tabName);
                return 0;
            }
        } else {
            List<TerminalSession> sessions = manager.findGuideSessions();
            if (sessions.isEmpty()) {
                sendError("No guide session found. Run /claude open first.");
                return 0;
            }
            if (sessions.size() > 1) {
                sendClaudeMessage("Multiple guide sessions. Use: /claude @<tabname> <message>");
                for (TerminalSession s : sessions) {
                    sendClaudeMessage("  @" + s.getName());
                }
                return 0;
            }
            target = sessions.get(0);
        }

        return sendToSession(target, message);
    }

    private static final Style USER_PREFIX_STYLE = Style.EMPTY.withColor(0x55FFFF); // Aqua

    private static int sendToSession(TerminalSession session, String message) {
        try {
            // Show the user's message in chat
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MutableComponent component = Component.literal("[You → " + session.getName() + "] ").withStyle(USER_PREFIX_STYLE)
                        .append(Component.literal(message).withStyle(CLAUDE_BODY_STYLE));
                mc.player.sendSystemMessage(component);
            }
            ClaudeGuideManager.getInstance().sendMessage(session, message);
        } catch (Exception e) {
            sendError("Failed to send: " + e.getMessage());
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ClaudeGuideManager manager = ClaudeGuideManager.getInstance();
        List<TerminalSession> sessions = manager.findGuideSessions();
        HookServer hookServer = HookServer.getInstance();

        if (sessions.isEmpty()) {
            sendClaudeMessage("No guide sessions active.");
        } else {
            sendClaudeMessage("Guide sessions (" + sessions.size() + "):");
            for (TerminalSession s : sessions) {
                sendClaudeMessage("  [" + s.getName() + "] " + (s.isAlive() ? "alive" : "dead"));
            }
        }

        if (hookServer.isRunning()) {
            sendClaudeMessage("Hook server: port " + hookServer.getPort());
        }

        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        HookServer.getInstance().stop();
        sendSuccess("Claude Guide stopped.");
        return 1;
    }

    public static void sendClaudeMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (String chunk : splitMessage(message)) {
            MutableComponent component = Component.literal("[Claude] ").withStyle(CLAUDE_PREFIX_STYLE)
                    .append(Component.literal(chunk).withStyle(CLAUDE_BODY_STYLE));
            mc.player.sendSystemMessage(component);
        }
    }

    public static void sendClaudeResponse(String sessionName, String response) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String prefix = "[" + sessionName + "] ";
        String indent = "  ";
        String[] lines = response.split("\n", -1); // -1 to keep trailing empty lines
        for (int i = 0; i < lines.length; i++) {
            String linePrefix = (i == 0) ? prefix : indent;
            Style prefixStyle = (i == 0) ? CLAUDE_PREFIX_STYLE : CLAUDE_BODY_STYLE;
            for (String chunk : splitMessage(lines[i])) {
                MutableComponent component = Component.literal(linePrefix).withStyle(prefixStyle)
                        .append(Component.literal(chunk).withStyle(CLAUDE_BODY_STYLE));
                mc.player.sendSystemMessage(component);
            }
        }
    }

    private static void sendError(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MutableComponent component = Component.literal("[Claude] ").withStyle(CLAUDE_PREFIX_STYLE)
                .append(Component.literal(message).withStyle(ERROR_STYLE));
        mc.player.sendSystemMessage(component);
    }

    private static void sendSuccess(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MutableComponent component = Component.literal("[Claude] ").withStyle(CLAUDE_PREFIX_STYLE)
                .append(Component.literal(message).withStyle(SUCCESS_STYLE));
        mc.player.sendSystemMessage(component);
    }

    private static String[] splitMessage(String message) {
        if (message.length() <= MAX_CHAT_LINE_LENGTH) {
            return new String[]{message};
        }
        int chunks = (message.length() + MAX_CHAT_LINE_LENGTH - 1) / MAX_CHAT_LINE_LENGTH;
        String[] result = new String[chunks];
        for (int i = 0; i < chunks; i++) {
            int start = i * MAX_CHAT_LINE_LENGTH;
            int end = Math.min(start + MAX_CHAT_LINE_LENGTH, message.length());
            result[i] = message.substring(start, end);
        }
        return result;
    }

    public static void cleanup() {
        HookServer.getInstance().stop();
    }
}
