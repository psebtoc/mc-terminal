# Terminal Mod

Minecraft Forge 1.20.1 mod that embeds a real OS terminal emulator inside Minecraft.
Primary use case: running Claude Code (CLI) within the game.

## Build & Run

```bash
./gradlew build        # Build the mod
./gradlew runClient    # Launch Minecraft with the mod
```

## Project Structure

```
com.pyosechang.terminal
├── TerminalMod.java              # @Mod entry point, registers blocks/items/configs
├── ModBlocks.java                # DeferredRegister for blocks
├── ModItems.java                 # DeferredRegister for items (BlockItem)
├── NotebookBlock.java            # Placeable notebook block, opens terminal on right-click
├── client/
│   ├── ClientSetup.java          # Client-side init, keybind (F12), config screen registration
│   ├── TerminalScreen.java       # Main terminal GUI (fullscreen + 70% windowed mode)
│   ├── TerminalRenderer.java     # Renders terminal buffer (ANSI colors, wide chars, cursor)
│   └── TerminalConfigScreen.java # In-game settings UI (Mods menu)
├── claude/
│   ├── ClaudeGuideCommand.java   # /claude command: open, onboarding, @tabname messaging
│   ├── ClaudeGuideManager.java   # Guide session detection (gameDir match), PTY message relay
│   ├── HookServer.java           # JDK HttpServer on localhost:0, receives Claude Code hooks
│   └── HookSetup.java            # Creates hook script, registers in project-local .claude/settings.json
├── terminal/
│   ├── TerminalSession.java      # PTY process + jediterm emulation per tab
│   ├── TerminalSessionManager.java # Multi-tab session lifecycle
│   ├── TerminalConfig.java       # Forge config (CLIENT: global, SERVER: per-world)
│   ├── TerminalDisplayBridge.java # jediterm TerminalDisplay adapter
│   └── InputStreamDataStream.java # Non-blocking PTY stream reader
└── util/
    └── KeyMapper.java            # Minecraft keycode → terminal escape sequence mapping
```

## Key Architecture Decisions

- **jediterm** for terminal emulation (VT100/xterm), **pty4j** for OS process — both shadowed into the mod jar
- Terminal rendering uses Minecraft's `GuiGraphics` with a custom TTF font (JetBrains Mono), not the default MC bitmap font
- GUI scale can be overridden independently from Minecraft's scale setting (pose().scale())
- Wide character handling: U+25CF (●) treated as double-width in renderer only, not via jediterm's global `ambiguousCharsAreDoubleWidth` (which breaks box-drawing chars)
- Config uses Forge's `ForgeConfigSpec` — CLIENT type for global settings, SERVER type for per-world start directory

## Terminal Access

- **F12 key**: Opens terminal in fullscreen mode
- **Notebook block**: Place in world, right-click → opens terminal in 70% windowed mode
- **Creative tab**: "Terminal" tab contains the notebook item

## Claude Guide System (`/claude` command)

In-game chat interface to Claude Code running in terminal tabs.

### Commands
- `/claude onboarding` — One-time setup: hook script, `.claude/settings.json`, `CLAUDE.md`
- `/claude open` — Create guide terminal tab (gameDir) + auto-run `claude`
- `/claude <message>` — Send to guide session (auto-select if single)
- `/claude @Guide2 <message>` — Send to specific guide session by tab name
- `/claude status` / `/claude stop`

### Architecture
- Guide session = terminal tab whose startDirectory == gameDir
- Messages sent by writing to PTY input (`\r` for Enter on Windows)
- Responses captured via Claude Code **Stop hook** → HookServer HTTP endpoint → chat display
- Hook registered in project-local `<gameDir>/.claude/settings.json` (not global)
- `SessionStart` hook maps Claude Code `session_id` → tab name for multi-session support
- Tab names have no spaces (validated in TerminalScreen) for `@tabname` parsing

## Known Constraints

- Terminal runs within Minecraft's JVM process (shares CPU/RAM allocation)
- DWC padding character U+E000 must be skipped during rendering
- Cursor visibility is read via reflection on jediterm's internal `myModes` field
