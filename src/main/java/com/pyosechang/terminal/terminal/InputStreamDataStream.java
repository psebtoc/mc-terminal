package com.pyosechang.terminal.terminal;

import com.jediterm.terminal.TerminalDataStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Adapts an InputStream to jediterm's TerminalDataStream interface.
 */
public class InputStreamDataStream implements TerminalDataStream {

    private final Reader reader;
    private char[] pushBackBuffer;
    private int pushBackOffset;
    private int pushBackLength;

    public InputStreamDataStream(InputStream inputStream) {
        this.reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }

    @Override
    public char getChar() throws IOException {
        if (pushBackBuffer != null && pushBackOffset < pushBackLength) {
            return pushBackBuffer[pushBackOffset++];
        }
        pushBackBuffer = null;

        int c = reader.read();
        if (c == -1) {
            throw new EOF();
        }
        return (char) c;
    }

    @Override
    public void pushChar(char c) throws EOF {
        char[] buf = new char[]{c};
        pushBackBuffer(buf, 1);
    }

    @Override
    public String readNonControlCharacters(int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < maxChars) {
            char c = getChar();
            if (c < 32 || c == 127) {
                pushChar(c);
                break;
            }
            sb.append(c);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @Override
    public void pushBackBuffer(char[] chars, int length) throws EOF {
        if (pushBackBuffer != null && pushBackOffset < pushBackLength) {
            // Merge with existing pushback
            int remaining = pushBackLength - pushBackOffset;
            char[] merged = new char[length + remaining];
            System.arraycopy(chars, 0, merged, 0, length);
            System.arraycopy(pushBackBuffer, pushBackOffset, merged, length, remaining);
            pushBackBuffer = merged;
            pushBackOffset = 0;
            pushBackLength = merged.length;
        } else {
            pushBackBuffer = chars.clone();
            pushBackOffset = 0;
            pushBackLength = length;
        }
    }

    @Override
    public boolean isEmpty() {
        if (pushBackBuffer != null && pushBackOffset < pushBackLength) {
            return false;
        }
        try {
            return !reader.ready();
        } catch (IOException e) {
            return true;
        }
    }
}
