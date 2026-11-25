/*
 * Copyright 2026 The Struts1-Forever Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.struts.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Local HTTP listener that counts the connections made to it, so that a test
 * can assert that a parser did not try to fetch a URL written in a document.
 *
 * <p>Every request gets an empty <code>200</code> response, so a parser that
 * does fetch the URL succeeds and the test sees the connection rather than an
 * I/O error.</p>
 */
public class ConnectionCounter {

    private static ConnectionCounter shared;

    /**
     * @return a listener shared by all tests in this JVM; compare
     *  {@link #getCount()} before and after the code under test
     */
    public static synchronized ConnectionCounter shared() throws IOException {
        if (shared == null) {
            shared = new ConnectionCounter();
        }
        return shared;
    }

    private final ServerSocket server;
    private final Thread acceptor;
    private int count;

    private ConnectionCounter() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        acceptor = new Thread(new Runnable() {
            public void run() {
                acceptLoop();
            }
        }, "ConnectionCounter");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * @param path path part of the URL, starting with "/"
     * @return an http URL on this listener
     */
    public String url(String path) {
        return "http://127.0.0.1:" + server.getLocalPort() + path;
    }

    public synchronized int getCount() {
        return count;
    }

    private void acceptLoop() {
        while (true) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                return;
            }
            synchronized (this) {
                count++;
            }
            try {
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n"
                    + "Content-Length: 0\r\nConnection: close\r\n\r\n")
                    .getBytes("US-ASCII"));
                out.flush();
                socket.close();
            } catch (IOException e) {
                // The client may have gone away; the connection is counted.
            }
        }
    }
}
