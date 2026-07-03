package com.infopublish.client.utils.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes complete log lines as ENC1 Base64 text before writing them.
 */
public class EncodedLogOutputStream extends OutputStream {

    public static final String PREFIX = "ENC1:";

    private static final byte CARRIAGE_RETURN = '\r';
    private static final byte LINE_FEED = '\n';
    private static final byte[] LINE_SEPARATOR = System.lineSeparator().getBytes(StandardCharsets.UTF_8);

    private final OutputStream delegate;
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);

    public EncodedLogOutputStream(OutputStream delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate output stream must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public synchronized void write(int value) throws IOException {
        byte byteValue = (byte) value;
        if (byteValue == LINE_FEED) {
            writeEncodedLine();
            return;
        }
        lineBuffer.write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (offset < 0 || length < 0 || length > bytes.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        for (int index = 0; index < length; index++) {
            write(bytes[offset + index]);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (lineBuffer.size() > 0) {
            writeEncodedLine();
        }
        delegate.flush();
    }

    private void writeEncodedLine() throws IOException {
        byte[] rawLine = trimTrailingCarriageReturn(lineBuffer.toByteArray());
        lineBuffer.reset();

        String lineText = new String(rawLine, StandardCharsets.UTF_8);
        String outputLine;
        if (lineText.startsWith(PREFIX)) {
            outputLine = lineText;
        } else {
            outputLine = PREFIX + Base64.getEncoder().encodeToString(rawLine);
        }

        delegate.write(outputLine.getBytes(StandardCharsets.UTF_8));
        delegate.write(LINE_SEPARATOR);
        delegate.flush();
    }

    private byte[] trimTrailingCarriageReturn(byte[] bytes) {
        if (bytes.length > 0 && bytes[bytes.length - 1] == CARRIAGE_RETURN) {
            byte[] trimmedBytes = new byte[bytes.length - 1];
            System.arraycopy(bytes, 0, trimmedBytes, 0, trimmedBytes.length);
            return trimmedBytes;
        }
        return bytes;
    }
}
