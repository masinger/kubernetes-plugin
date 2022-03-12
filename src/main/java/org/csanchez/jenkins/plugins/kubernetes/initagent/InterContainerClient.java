package org.csanchez.jenkins.plugins.kubernetes.initagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.AttachStreamMessage;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.ControlMessage;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.OpenSessionResponse;
import org.csanchez.jenkins.plugins.kubernetes.initagent.model.StartSessionMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class InterContainerClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    public static final byte CONTROL_MESSAGE_SEPARATOR = 0x0a; // = "\n"

    private void send(OutputStream out, ControlMessage message) throws IOException {
        out.write(objectMapper.writeValueAsBytes(message));
        out.write(CONTROL_MESSAGE_SEPARATOR);
        out.flush();
    }

    private <T> T read(InputStream in, Class<T> type) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        while((read = in.read()) != -1) {
            if (read == CONTROL_MESSAGE_SEPARATOR) {
                break;
            }
            buffer.write(read);
        }
        return objectMapper.readValue(buffer.toByteArray(), type);
    }

    public InterContainerChannel connect(String host, int port) throws IOException {
        Socket controlSocket = new Socket(host, port);
        OutputStream controlOutStream = controlSocket.getOutputStream();
        InputStream controlInStream = controlSocket.getInputStream();

        send(controlOutStream, new StartSessionMessage());
        OpenSessionResponse response = read(controlInStream, OpenSessionResponse.class);

        Socket outSocket = new Socket(host, port);
        send(outSocket.getOutputStream(), new AttachStreamMessage(response.getSession(), AttachStreamMessage.OUT_STREAM));

        Socket errSocket = new Socket(host, port);
        send(errSocket.getOutputStream(), new AttachStreamMessage(response.getSession(), AttachStreamMessage.ERR_STREAM));


        return new InterContainerChannel(objectMapper, controlSocket, outSocket, errSocket);
    }

}
