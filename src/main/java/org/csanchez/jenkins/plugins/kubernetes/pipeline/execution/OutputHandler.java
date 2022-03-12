package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import org.apache.commons.io.output.TeeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.ToggleOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class OutputHandler {

   private final PrintStream printStream;
   private final OutputStream stream;
   private final ByteArrayOutputStream stdout;
   private final ToggleOutputStream toggleStdout;
   private final ByteArrayOutputStream error;

    public OutputHandler(
            PrintStream printStream,
            OutputStream stream,
            ByteArrayOutputStream stdout, ToggleOutputStream toggleStdout, ByteArrayOutputStream error) {
        this.printStream = printStream;
        this.stream = stream;
        this.stdout = stdout;
        this.toggleStdout = toggleStdout;
        this.error = error;
    }


    public static OutputHandler create(boolean quiet, OutputStream outputForCaller, PrintStream launcherLogStream) throws IOException {

        PrintStream printStream;
        OutputStream stream;

        // Only output to stdout at the beginning for diagnostics.
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ToggleOutputStream toggleStdout = new ToggleOutputStream(stdout);

        // Do not send this command to the output when in quiet mode
        if (quiet) {
            stream = toggleStdout;
            printStream = new PrintStream(stream, true, StandardCharsets.UTF_8.toString());
        } else {
            printStream = launcherLogStream;
            stream = new TeeOutputStream(toggleStdout, printStream);
        }

        // Send to proc caller as well if they sent one
        if (outputForCaller != null && !outputForCaller.equals(printStream)) {
            stream = new TeeOutputStream(outputForCaller, stream);
        }
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        return new OutputHandler(
                printStream,
                stream,
                stdout,
                toggleStdout,
                error
        );

    }

    public PrintStream getPrintStream() {
        return printStream;
    }

    public ByteArrayOutputStream getStdout() {
        return stdout;
    }

    public OutputStream getStream() {
        return stream;
    }

    public ByteArrayOutputStream getError() {
        return error;
    }

    public ToggleOutputStream getToggleStdout() {
        return toggleStdout;
    }
}
