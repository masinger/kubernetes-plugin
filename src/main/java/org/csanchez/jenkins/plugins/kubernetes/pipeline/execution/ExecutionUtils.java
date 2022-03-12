package org.csanchez.jenkins.plugins.kubernetes.pipeline.execution;

import hudson.EnvVars;
import hudson.FilePath;
import org.apache.commons.io.output.TeeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.MaskOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.EXIT;

public final class ExecutionUtils {

    private static final Logger LOGGER = Logger.getLogger(ExecutionUtils.class.getName());

    private ExecutionUtils() {

    }

    public static void doExec(PrintStream in, boolean windows, PrintStream out, boolean[] masks, String... statements) {
        long start = System.nanoTime();
        // For logging
        ByteArrayOutputStream loggingOutput = new ByteArrayOutputStream();
        // Tee both outputs
        TeeOutputStream teeOutput = new TeeOutputStream(out, loggingOutput);
        // Mask sensitive output
        MaskOutputStream maskedOutput = new MaskOutputStream(teeOutput, masks);
        // Tee everything together
        PrintStream tee = null;
        try {
            String encoding = StandardCharsets.UTF_8.name();
            tee = new PrintStream(new TeeOutputStream(in, maskedOutput), false, encoding);
            // To output things that shouldn't be considered for masking
            PrintStream unmasked = new PrintStream(teeOutput, false, encoding);
            unmasked.print("Executing command: ");
            for (String statement : statements) {
                tee.append("\"")
                        .append(statement)
                        .append("\" ");
            }
            tee.print(newLine(windows));
            LOGGER.log(Level.FINEST, loggingOutput.toString(encoding) + "[" + TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start) + " μs." + "]");
            // We need to exit so that we know when the command has finished.
            tee.print(EXIT);
            tee.print(newLine(windows));
            tee.flush();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public static void setupEnvironmentVariable(EnvVars vars, PrintStream out, boolean windows) throws IOException {
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            //Check that key is bash compliant.
            if (entry.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                out.print(
                        String.format(
                                windows ? "set %s=%s" : "export %s='%s'",
                                entry.getKey(),
                                windows ? entry.getValue() : entry.getValue().replace("'", "'\\''")
                        )
                );
                out.print(ExecutionUtils.newLine(windows));
            }
        }
    }

    public static void setWorkingDir(
            PrintStream in,
            FilePath optionalWorkingDir,
            boolean isWindows
    ) {
        if (optionalWorkingDir != null) {
            // We need to get into the project workspace.
            // The workspace is not known in advance, so we have to execute a cd command.
            in.print(String.format("cd \"%s\"", optionalWorkingDir));
            in.print(newLine(isWindows));
        }
    }

    public static String newLine(boolean windows) {
        return windows ? "\r\n" : "\n";
    }
}
