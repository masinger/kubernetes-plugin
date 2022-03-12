package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Process given stream and mask as specified by the bitfield.
 * Uses space as a separator to determine which fragments to hide.
 */
public class MaskOutputStream extends FilterOutputStream {
    private static final String MASK_STRING = "********";

    private final boolean[] masks;
    private final static char SEPARATOR = ' ';
    private int index;
    private boolean wrote;


    public MaskOutputStream(OutputStream out, boolean[] masks) {
        super(out);
        this.masks = masks;
    }

    @Override
    public void write(int b) throws IOException {
        if (masks == null || index >= masks.length) {
            out.write(b);
        } else if (isSeparator(b)) {
            out.write(b);
            index++;
            wrote = false;
        } else if (masks[index]) {
            if (!wrote) {
                wrote = true;
                for (char c : MASK_STRING.toCharArray()) {
                    out.write(c);
                }
            }
        } else {
            out.write(b);
        }
    }

    private boolean isSeparator(int b) {
        return b == SEPARATOR;
    }
}
