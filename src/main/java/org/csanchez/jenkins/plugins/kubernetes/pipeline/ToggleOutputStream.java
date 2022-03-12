package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ToggleOutputStream extends FilterOutputStream {
    private boolean disabled;

    public ToggleOutputStream(OutputStream out) {
        super(out);
    }

    public void disable() {
        disabled = true;
    }

    public void enable() {
        disabled = false;
    }

    @Override
    public void write(int b) throws IOException {
        if (!disabled) {
            out.write(b);
        }
    }
}
