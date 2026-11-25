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
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;

/**
 * <p>The XML parsing policy of Struts.</p>
 *
 * <p>A <code>Digester</code> that follows this policy may parse a document
 * with a <code>DOCTYPE</code> declaration, and the following holds for every
 * document it parses:</p>
 * <ul>
 * <li>External general and parameter entities are not expanded. A document
 *     or DTD that declares an external general entity fails to parse, so
 *     that a reference to it is not silently left out.</li>
 * <li>An external DTD or entity is read only from a source that the
 *     application provided: a resource registered with
 *     {@link Digester#register(String, String)}, or what an
 *     <code>EntityResolver</code> set with
 *     {@link Digester#setEntityResolver(EntityResolver)} returns. Struts never
 *     falls back to opening a URI written in the document. A registration or a
 *     resolver is the application's explicit permission, and any I/O it causes
 *     is the application's responsibility.</li>
 * <li>Entity expansion is limited, so a document cannot make the parser
 *     expand internal entities without bound.</li>
 * <li>XInclude is not processed.</li>
 * </ul>
 * <p>Whether the <code>Digester</code> validates does not change any of
 * this. If the XML parser cannot be made to meet one of these conditions,
 * parsing fails with a <code>SAXException</code> instead of going ahead
 * without it.</p>
 *
 * <p>Struts creates its own <code>Digester</code> instances as
 * {@link SecureDigester}, which applies this policy whenever it parses. Use
 * {@link #secure(Digester)} for a <code>Digester</code> created elsewhere, for
 * example by an application's <code>DigestingPlugIn</code> subclass, right
 * before it parses.</p>
 *
 * @since Struts 1.2.9-forever-4
 */
public final class DigesterSecurity {

    private static final Log log = LogFactory.getLog(DigesterSecurity.class);

    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
        "http://xml.org/sax/features/external-general-entities";

    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
        "http://xml.org/sax/features/external-parameter-entities";

    private static final String FEATURE_SECURE_PROCESSING =
        "http://javax.xml.XMLConstants/feature/secure-processing";

    /** Entity expansion limit of Xerces versions without secure processing. */
    private static final String PROPERTY_XERCES_SECURITY_MANAGER =
        "http://apache.org/xml/properties/security-manager";

    private static final String XERCES_SECURITY_MANAGER_CLASS =
        "org.apache.xerces.util.SecurityManager";

    private static final String FEATURE_XINCLUDE =
        "http://apache.org/xml/features/xinclude";

    private static final String PROPERTY_DECLARATION_HANDLER =
        "http://xml.org/sax/properties/declaration-handler";

    private static final String PROPERTY_ACCESS_EXTERNAL_DTD =
        "http://javax.xml.XMLConstants/property/accessExternalDTD";

    private static final String PROPERTY_ACCESS_EXTERNAL_SCHEMA =
        "http://javax.xml.XMLConstants/property/accessExternalSchema";

    private DigesterSecurity() {
    }

    /**
     * <p>Apply the policy to a configured <code>Digester</code> that is about
     * to parse.</p>
     *
     * <p>The <code>Digester</code> creates its XML parser from its current
     * configuration at this point, so configure it (validation, namespace
     * awareness, DTD registrations) first. Calling this again on the same
     * <code>Digester</code> keeps the policy.</p>
     *
     * @param digester the Digester that is about to parse
     * @throws SAXException if the XML parser cannot be created or cannot be
     *  made to follow the policy; the Digester must not be used to parse then
     */
    public static void secure(Digester digester) throws SAXException {
        if (digester instanceof SecureDigester) {
            // Applies the policy itself whenever it hands out its reader.
            digester.getXMLReader();
            return;
        }

        EntityResolver current = digester.getEntityResolver();
        if (!(current instanceof RegisteredEntityResolver)
            || ((RegisteredEntityResolver) current).digester != digester) {
            digester.setEntityResolver(
                new RegisteredEntityResolver(digester, current));
        }
        configure(readerOf(digester));
    }

    /**
     * Apply the policy to the reader of a {@link SecureDigester}. The
     * Digester installs its own entity resolver on the reader each time it
     * hands it out, so the resolver is set on the reader here.
     */
    static void secure(SecureDigester digester, XMLReader reader)
            throws SAXException {
        reader.setEntityResolver(
            new RegisteredEntityResolver(digester, digester.getEntityResolver()));
        configure(reader);
    }

    private static XMLReader readerOf(Digester digester) throws SAXException {
        XMLReader reader;
        try {
            reader = digester.getXMLReader();
        } catch (RuntimeException e) {
            throw new SAXException("Cannot create the XML parser", e);
        }
        if (reader == null) {
            throw new SAXException("Cannot create the XML parser");
        }
        return reader;
    }

    private static void configure(XMLReader reader) throws SAXException {
        requireFeature(reader, FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        requireFeature(reader, FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        requireEntityExpansionLimit(reader);
        requireNoXInclude(reader);
        rejectExternalEntityDeclarations(reader);

        // The entity resolver already decides which DTDs are read; these
        // properties also stop the parser itself where it supports them.
        setOptionalProperty(reader, PROPERTY_ACCESS_EXTERNAL_DTD, "");
        setOptionalProperty(reader, PROPERTY_ACCESS_EXTERNAL_SCHEMA, "");
    }

    private static void requireFeature(XMLReader reader, String name, boolean value)
            throws SAXException {
        try {
            reader.setFeature(name, value);
            if (reader.getFeature(name) == value) {
                return;
            }
        } catch (SAXException e) {
            throw unsafeParser(reader, "does not let " + name + " be set to " + value, e);
        }
        throw unsafeParser(reader, "keeps " + name + " at " + !value, null);
    }

    /**
     * Secure processing limits entity expansion in the JDK parser and in
     * Xerces 2.7 and later. Older Xerces versions limit it with a
     * SecurityManager instead.
     */
    private static void requireEntityExpansionLimit(XMLReader reader)
            throws SAXException {
        try {
            reader.setFeature(FEATURE_SECURE_PROCESSING, true);
            if (reader.getFeature(FEATURE_SECURE_PROCESSING)) {
                return;
            }
        } catch (SAXException e) {
            // Not supported; try the Xerces security manager below.
        }

        try {
            if (reader.getProperty(PROPERTY_XERCES_SECURITY_MANAGER) == null) {
                Class managerClass = Class.forName(XERCES_SECURITY_MANAGER_CLASS,
                    true, reader.getClass().getClassLoader());
                reader.setProperty(PROPERTY_XERCES_SECURITY_MANAGER,
                    managerClass.newInstance());
            }
            if (reader.getProperty(PROPERTY_XERCES_SECURITY_MANAGER) != null) {
                return;
            }
        } catch (SAXException e) {
            // Not a Xerces parser.
        } catch (ClassNotFoundException e) {
            // Not a Xerces parser.
        } catch (InstantiationException e) {
            // Not usable.
        } catch (IllegalAccessException e) {
            // Not usable.
        }
        throw unsafeParser(reader, "cannot limit entity expansion", null);
    }

    private static void requireNoXInclude(XMLReader reader) throws SAXException {
        try {
            reader.setFeature(FEATURE_XINCLUDE, false);
        } catch (SAXNotRecognizedException e) {
            // The parser does not implement XInclude.
            return;
        } catch (SAXException e) {
            // Recognized but not changeable; check its current value.
        }
        try {
            if (!reader.getFeature(FEATURE_XINCLUDE)) {
                return;
            }
        } catch (SAXException e) {
            throw unsafeParser(reader, "cannot report whether it processes XInclude", e);
        }
        throw unsafeParser(reader, "processes XInclude and cannot turn it off", null);
    }

    /**
     * Parsers report a reference to an unexpanded external entity
     * differently (Xerces 2.6.2 drops it without telling the content
     * handler), so the declaration is rejected instead.
     */
    private static void rejectExternalEntityDeclarations(XMLReader reader) {
        try {
            Object current = reader.getProperty(PROPERTY_DECLARATION_HANDLER);
            if (!(current instanceof ExternalEntityDeclarationGuard)) {
                reader.setProperty(PROPERTY_DECLARATION_HANDLER,
                    new ExternalEntityDeclarationGuard((DeclHandler) current));
            }
        } catch (SAXException e) {
            // Without declaration events, SecureDigester.skippedEntity()
            // reports the reference where the parser reports it.
            if (log.isDebugEnabled()) {
                log.debug("XML parser does not report entity declarations");
            }
        }
    }

    private static void setOptionalProperty(XMLReader reader, String name, Object value) {
        try {
            reader.setProperty(name, value);
        } catch (SAXException e) {
            if (log.isDebugEnabled()) {
                log.debug("XML parser does not support property " + name);
            }
        }
    }

    private static SAXException unsafeParser(XMLReader reader, String problem,
            Exception cause) {
        String message = "XML parser " + reader.getClass().getName() + " " + problem
            + ", so Struts does not use it to parse.";
        return cause == null ? new SAXException(message) : new SAXException(message, cause);
    }

    /** Fails on the declaration of an external general entity. */
    private static final class ExternalEntityDeclarationGuard implements DeclHandler {

        /** Declaration handler set on the reader before, or null. */
        private final DeclHandler next;

        ExternalEntityDeclarationGuard(DeclHandler next) {
            this.next = next;
        }

        public void externalEntityDecl(String name, String publicId, String systemId)
                throws SAXException {
            if (!name.startsWith("%")) {
                throw new SAXException("External entity '" + name + "' (system ID: "
                    + systemId + ") cannot be used because external entities are"
                    + " not expanded. Put its content into the document instead.");
            }
            if (next != null) {
                next.externalEntityDecl(name, publicId, systemId);
            }
        }

        public void internalEntityDecl(String name, String value) throws SAXException {
            if (next != null) {
                next.internalEntityDecl(name, value);
            }
        }

        public void elementDecl(String name, String model) throws SAXException {
            if (next != null) {
                next.elementDecl(name, model);
            }
        }

        public void attributeDecl(String elementName, String attributeName,
                String type, String mode, String value) throws SAXException {
            if (next != null) {
                next.attributeDecl(elementName, attributeName, type, mode, value);
            }
        }
    }

    /**
     * Resolves an external entity to a source that the application provided,
     * and rejects every other entity.
     */
    private static final class RegisteredEntityResolver implements EntityResolver {

        private final Digester digester;

        /** Resolver the application set on the Digester, or null. */
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
