package dev.rpmixer.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bricht ab, sobald mehr als {@code max} Bytes gelesen werden (Schutz vor gefälschten Größenangaben). */
final class LimitedInputStream extends FilterInputStream {
    private long remaining;

    LimitedInputStream(InputStream in, long max) {
        super(in);
        this.remaining = max;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) consume(1);
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) consume(n);
        return n;
    }

    private void consume(long n) throws IOException {
        remaining -= n;
        if (remaining < 0) throw new IOException("Datei größer als erlaubt");
    }
}
