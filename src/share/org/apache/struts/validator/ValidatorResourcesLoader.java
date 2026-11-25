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
package org.apache.struts.validator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.digester.Digester;
import org.apache.commons.validator.ValidatorResources;
import org.apache.struts.util.SecureDigester;
import org.xml.sax.SAXException;

/**
 * <p>Loads <code>ValidatorResources</code> from validation files that follow
 * the XML parsing policy of {@link org.apache.struts.util.DigesterSecurity}.</p>
 *
 * <p>Commons Validator parses with a <code>Digester</code> that it creates
 * internally, which reads any external DTD or entity a file names. Each
 * file is therefore read into memory and first parsed by a
 * {@link SecureDigester} that registers the same DTDs as Commons Validator.
 * Only if every file passes are the same bytes given to
 * <code>new ValidatorResources(InputStream[])</code> of the Commons
 * Validator version the application uses, so the files are loaded exactly
 * as that version loads them.</p>
 *
 * <p>The second parse reads nothing the first did not: the file declares no
 * external entity (the policy rejects that), its DTD is one that Commons
 * Validator registers itself (every version from 1.1.4 to 1.7 registers
 * every DTD it ships, and only shipped DTDs are registered here), and the
 * same bytes parsed by the same XML parser expand the same entities.</p>
 */
final class ValidatorResourcesLoader {

    /** The DTDs that Commons Validator 1.1.4 to 1.7 ship and register. */
    static final String[] REGISTRATIONS = {
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.0//EN",
        "/org/apache/commons/validator/resources/validator_1_0.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.0.1//EN",
        "/org/apache/commons/validator/resources/validator_1_0_1.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.1//EN",
        "/org/apache/commons/validator/resources/validator_1_1.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.1.3//EN",
        "/org/apache/commons/validator/resources/validator_1_1_3.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.2.0//EN",
        "/org/apache/commons/validator/resources/validator_1_2_0.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.3.0//EN",
        "/org/apache/commons/validator/resources/validator_1_3_0.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.4.0//EN",
        "/org/apache/commons/validator/resources/validator_1_4_0.dtd"
    };

    private ValidatorResourcesLoader() {
    }

    /**
     * Same as <code>new ValidatorResources(streams)</code>, after checking
     * every file against the XML parsing policy.
     *
     * @param streams validation files, loaded in this order
     * @return the loaded and processed resources
     * @throws IOException if a file cannot be read
     * @throws SAXException if a file does not follow the policy or cannot
     *  be parsed
     */
    static ValidatorResources load(InputStream[] streams)
            throws IOException, SAXException {
        InputStream[] checked = new InputStream[streams.length];
        for (int i = 0; i < streams.length; i++) {
            if (streams[i] == null) {
                throw new IllegalArgumentException("Stream[" + i + "] is null");
            }
            byte[] file = readFully(streams[i]);
            check(file);
            checked[i] = new ByteArrayInputStream(file);
        }
        return new ValidatorResources(checked);
    }

    private static void check(byte[] file) throws IOException, SAXException {
        Digester digester = new SecureDigester();
        for (int i = 0; i < REGISTRATIONS.length; i += 2) {
            // Registered only where this Commons Validator ships the DTD,
            // which is where it registers it too.
            URL url = ValidatorResources.class.getResource(REGISTRATIONS[i + 1]);
            if (url != null) {
                digester.register(REGISTRATIONS[i], url.toString());
            }
        }
        digester.parse(new ByteArrayInputStream(file));
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
