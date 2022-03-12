package org.csanchez.jenkins.plugins.kubernetes.initagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.ControlMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class InterContainerChannel {

    private final ObjectMapper objectMapper;
    private final Socket controlSocket;
    private final Socket outSocket;
    private final Socket errSocket;



    public InterContainerChannel(
            ObjectMapper objectMapper,
            Socket controlSocket,
            Socket outSocket,
            Socket errSocket
    ) {
        this.objectMapper = objectMapper;
        this.controlSocket = controlSocket;
        this.outSocket = outSocket;
        this.errSocket = errSocket;
    }


    public void sendMessage(ControlMessage message) throws IOException {
        OutputStream out = controlSocket.getOutputStream();
        out.write(objectMapper.writeValueAsBytes(message));
        out.write(InterContainerClient.CONTROL_MESSAGE_SEPARATOR);
        out.flush();
    }

    public <T> T readMessage(Class<T> type) throws IOException {
        InputStream in = controlSocket.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        while((read = in.read()) != -1) {
            if (read == InterContainerClient.CONTROL_MESSAGE_SEPARATOR) {
                break;
            }
            buffer.write(read);
        }
        return objectMapper.readValue(buffer.toByteArray(), type);
    }

    public InputStream getRemoteOutput() throws IOException {
        return outSocket.getInputStream();
    }

    public OutputStream getRemoteInput() throws IOException {
        return outSocket.getOutputStream();
    }

    public InputStream getRemoteError() throws IOException {
        return errSocket.getInputStream();
    }

}
