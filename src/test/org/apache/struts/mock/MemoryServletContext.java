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
package org.apache.struts.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link MockServletContext} whose resources are XML documents that a test
 * puts in memory, so a test can parse a webapp file without writing it to
 * disk.
 */
public class MemoryServletContext extends MockServletContext {

    private final Map documents = new HashMap();

    private final URLStreamHandler handler = new URLStreamHandler() {
        protected URLConnection openConnection(final URL url) {
            return new URLConnection(url) {
                public void connect() {
                }

                public InputStream getInputStream() throws IOException {
                    byte[] bytes = (byte[]) documents.get(url.getPath());
                    if (bytes == null) {
                        throw new IOException("not found: " + url);
                    }
                    return new ByteArrayInputStream(bytes);
                }
            };
        }
    };

    /**
     * @param path context-relative path, starting with "/"
     * @param xml the document after the XML declaration
     */
    public void put(String path, String xml) throws IOException {
        documents.put(path,
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xml).getBytes("UTF-8"));
    }

    public URL getResource(String path) {
        if (!documents.containsKey(path)) {
            return null;
        }
        try {
            return new URL("memory", null, -1, path, handler);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e.toString());
        }
    }

    public InputStream getResourceAsStream(String path) {
        URL url = getResource(path);
        if (url == null) {
            return null;
        }
        try {
            return url.openStream();
        } catch (IOException e) {
            return null;
        }
    }
}
