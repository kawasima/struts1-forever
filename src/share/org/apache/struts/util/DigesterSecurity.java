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

import org.apache.commons.digester.Digester;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * <p>Restricts the external resources that a <code>Digester</code> may read
 * while it parses a document.</p>
 *
 * <p>A secured <code>Digester</code> accepts a <code>DOCTYPE</code>
 * declaration, but resolves an external DTD or entity only to a resource
 * that trusted code registered with {@link Digester#register(String, String)}
 * or to the source returned by an <code>EntityResolver</code> that trusted
 * code installed with {@link Digester#setEntityResolver(EntityResolver)}.
 * Any other external reference makes the parse fail with a
 * <code>SAXException</code> before the parser reads anything from the
 * system identifier written in the document. This does not depend on whether
 * the <code>Digester</code> is validating.</p>
 *
 * <p>Call {@link #secure(Digester)} after the <code>Digester</code> has been
 * configured (validation, namespace awareness, DTD registrations) and before
 * it parses. The settings are applied to the <code>XMLReader</code> that the
 * <code>Digester</code> uses, which the <code>Digester</code> creates from its
 * configuration at that point and keeps for later parses.</p>
 *
 * @since Struts 1.2.9-forever-4
 */
public final class DigesterSecurity {

    private static final Log log = LogFactory.getLog(DigesterSecurity.class);

    /**
     * SAX features that every secured reader must support. If the reader
     * refuses one of them, {@link #secure(Digester)} fails.
     */
    private static final String[] REQUIRED_FEATURES_OFF = {
        "http://xml.org/sax/features/external-general-entities",
        "http://xml.org/sax/features/external-parameter-entities",
    };

    /**
     * JAXP and Xerces settings that further restrict the parser where they
     * are supported. Parsers that predate them (for example an old Xerces in
     * <code>WEB-INF/lib</code>) still parse, because the entity resolver
     * already decides what may be read.
     */
    private static final String FEATURE_SECURE_PROCESSING =
        "http://javax.xml.XMLConstants/feature/secure-processing";

    private static final String FEATURE_XINCLUDE =
        "http://apache.org/xml/features/xinclude";

    private static final String PROPERTY_ACCESS_EXTERNAL_DTD =
        "http://javax.xml.XMLConstants/property/accessExternalDTD";

    private static final String PROPERTY_ACCESS_EXTERNAL_SCHEMA =
        "http://javax.xml.XMLConstants/property/accessExternalSchema";

    private DigesterSecurity() {
    }

    /**
     * <p>Restrict the external resources that <code>digester</code> may read
     * while parsing.</p>
     *
     * <p>Calling this again on the same <code>Digester</code> keeps the
     * restriction and does not change it.</p>
     *
     * @param digester the configured Digester that is about to parse
     * @throws SAXException if the XML parser cannot be created or does not
     *  support disabling external entities; the Digester must not be used to
     *  parse in that case
     */
    public static void secure(Digester digester) throws SAXException {
        EntityResolver current = digester.getEntityResolver();
        if (!(current instanceof RegisteredEntityResolver)
            || ((RegisteredEntityResolver) current).digester != digester) {
            digester.setEntityResolver(
                new RegisteredEntityResolver(digester, current));
        }

        XMLReader reader;
        try {
            reader = digester.getXMLReader();
        } catch (RuntimeException e) {
            throw new SAXException("Cannot create the XML parser", e);
        }
        if (reader == null) {
            throw new SAXException("Cannot create the XML parser");
        }

        for (int i = 0; i < REQUIRED_FEATURES_OFF.length; i++) {
            reader.setFeature(REQUIRED_FEATURES_OFF[i], false);
        }

        setOptionalFeature(reader, FEATURE_SECURE_PROCESSING, true);
        setOptionalFeature(reader, FEATURE_XINCLUDE, false);
        setOptionalProperty(reader, PROPERTY_ACCESS_EXTERNAL_DTD, "");
        setOptionalProperty(reader, PROPERTY_ACCESS_EXTERNAL_SCHEMA, "");
    }

    private static void setOptionalFeature(
            XMLReader reader, String name, boolean value) {
        try {
            reader.setFeature(name, value);
        } catch (SAXException e) {
            if (log.isDebugEnabled()) {
                log.debug("XML parser does not support feature " + name);
            }
        }
    }

    private static void setOptionalProperty(
            XMLReader reader, String name, Object value) {
        try {
            reader.setProperty(name, value);
        } catch (SAXException e) {
            if (log.isDebugEnabled()) {
                log.debug("XML parser does not support property " + name);
            }
        }
    }

    /**
     * Resolves an external entity to a source that trusted code provided,
     * and rejects every other entity.
     */
    private static final class RegisteredEntityResolver
            implements EntityResolver {

        private final Digester digester;

        /** Resolver installed on the Digester before it was secured, or null. */
        private final EntityResolver installed;

        RegisteredEntityResolver(Digester digester, EntityResolver installed) {
            this.digester = digester;
            this.installed = installed;
        }

        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {

            if (installed != null) {
                InputSource source = installed.resolveEntity(publicId, systemId);
                if (source != null) {
                    return source;
                }
            }

            // Without a system identifier, Digester.resolveEntity() returns
            // only a registered resource and never falls back to opening the
            // system identifier. It also records the public identifier,
            // which Digester.getPublicId() reports.
            if (publicId != null) {
                InputSource source = digester.resolveEntity(publicId, null);
                if (source != null) {
                    return source;
                }
            }

            // A DOCTYPE without a public identifier can be served locally by
            // registering its system identifier.
            if (systemId != null) {
                String recordedPublicId = digester.getPublicId();
                InputSource source = digester.resolveEntity(systemId, null);
                digester.setPublicId(recordedPublicId);
                if (source != null) {
                    return source;
                }
            }

            throw new SAXException(
                "External XML resource is not registered (public ID: "
                + publicId + ", system ID: " + systemId + "). Register a local"
                + " copy with Digester.register() or remove the reference.");
        }
    }
}
