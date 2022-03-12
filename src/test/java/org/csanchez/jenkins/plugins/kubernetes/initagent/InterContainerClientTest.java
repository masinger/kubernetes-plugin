package org.csanchez.jenkins.plugins.kubernetes.initagent;

import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.ProcessTerminatedResponse;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.StartProcessMessage;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class InterContainerClientTest {


    @Test
    public void test() throws IOException {
        InterContainerClient c = new InterContainerClient();
        InterContainerChannel containerChannel = c.connect("localhost", 1200);

        InputStream remoteOutput = containerChannel.getRemoteOutput();

        Thread copyOutput = new Thread(() -> {
            try {
                IOUtils.copy(remoteOutput, System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        copyOutput.start();

        containerChannel.sendMessage(new StartProcessMessage("sh"));
        PrintStream ps = new PrintStream(containerChannel.getRemoteInput(), true ,StandardCharsets.UTF_8.name());
        ps.print("ls /\n");
        ps.flush();
        ps.print("exit\n");
        ps.flush();

        ProcessTerminatedResponse processTerminatedResponse = containerChannel.readMessage(ProcessTerminatedResponse.class);

        return;

    }

}