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
import java.lang.reflect.Method;

import org.apache.commons.digester.Digester;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;

/**
 * <p>The XML parsing policy of Struts.</p>
 *
 * <p>A <code>Digester</code> that follows this policy may parse a document
 * with a <code>DOCTYPE</code> declaration, and the following holds for every
 * document it parses, whether or not it validates:</p>
 * <ul>
 * <li>An external DTD or entity is read only from a source that the
 *     application provided: a resource registered with
 *     {@link Digester#register(String, String)}, or what an
 *     <code>EntityResolver</code> set with
 *     {@link Digester#setEntityResolver(EntityResolver)} returns. Struts never
 *     falls back to opening a URI written in the document; this also covers
 *     XInclude and XML Schema documents, which parsers resolve the same way. A
 *     registration or a resolver is the application's explicit permission,
 *     and any I/O it causes is the application's responsibility.</li>
 * <li>External entities are not expanded. A document or DTD that declares an
 *     external general entity fails to parse, so that a reference to it is
 *     not silently left out.</li>
 * <li>A document expands to at most {@link #MAX_ENTITY_EXPANSIONS} entity
 *     expansions of at most {@link #MAX_ENTITY_LENGTH} characters each. The
 *     parser counts the expansions; its limit is lowered to
 *     <code>MAX_ENTITY_EXPANSIONS</code> if it is higher or unlimited and kept
 *     if it is lower. Struts rejects the declaration of an internal entity
 *     whose replacement text is longer than <code>MAX_ENTITY_LENGTH</code>.
 *     Together these bound the expanded text of a document, on parsers that
 *     limit only the number of expansions (Apache Xerces) as well.</li>
 * <li>XInclude is not processed by parsers that let it be turned off.</li>
 * <li>A fatal error stops parsing, also on a parser set to continue after
 *     fatal errors, so a limit the parser enforces is not reported and then
 *     ignored.</li>
 * </ul>
 * <p>Applying the policy never relaxes a restriction the parser already has:
 * a stricter limit is kept, and limits Struts does not set are left as they
 * are. If the XML parser cannot be made to meet these conditions, parsing
 * fails with a <code>SAXException</code> instead of going ahead without them.
 * The JDK parser and Apache Xerces 2.x meet them.</p>
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

    /** Highest entity expansion count a parser may allow. */
    public static final int MAX_ENTITY_EXPANSIONS = 10000;

    /** Longest replacement text of an internal entity, in characters. */
    public static final int MAX_ENTITY_LENGTH = 5000;

    private static final Log log = LogFactory.getLog(DigesterSecurity.class);

    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES =
        "http://xml.org/sax/features/external-general-entities";

    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES =
        "http://xml.org/sax/features/external-parameter-entities";

    /** Entity expansion limit of the JDK parser. */
    private static final String PROPERTY_JDK_ENTITY_EXPANSION_LIMIT =
        "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit";

    /** Holds the entity expansion limit of Apache Xerces. */
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
     * <code>Digester</code> keeps the policy. A {@link SecureDigester} applies
     * the policy itself when it parses, so nothing is done for one here.</p>
     *
     * @param digester the Digester that is about to parse
     * @throws SAXException if the XML parser cannot be created or cannot be
     *  made to follow the policy; the Digester must not be used to parse then
     */
    public static void secure(Digester digester) throws SAXException {
        if (digester instanceof SecureDigester) {
            return;
        }

        EntityResolver current = digester.getEntityResolver();
        if (!(current instanceof RegisteredEntityResolver)
            || ((RegisteredEntityResolver) current).digester != digester) {
            digester.setEntityResolver(
                new RegisteredEntityResolver(digester, current));
        }
        // Digester.fatalError() only passes the error on to this handler.
        ErrorHandler errorHandler = digester.getErrorHandler();
        if (!(errorHandler instanceof FatalErrorGuard)) {
            digester.setErrorHandler(new FatalErrorGuard(errorHandler));
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
        configure(reader);
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

    private static void configure(XMLReader reader) throws SAXException {
        requireFeature(reader, FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        requireFeature(reader, FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        requireEntityExpansionLimit(reader);
        requireDeclarationGuard(reader);
        requireNoXInclude(reader);

        // The entity resolver already decides what is read; these properties
        // also stop the parser itself where it supports them.
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
     * Make the parser count entity expansions with a limit between 1 and
     * {@link #MAX_ENTITY_EXPANSIONS}, and read the limit back. Secure
     * processing is not used: it does not establish this (an explicit limit
     * of 0, for example from the <code>jdk.xml.entityExpansionLimit</code>
     * system property, stays in effect with it), and on Apache Xerces it
     * replaces the security manager and with it any stricter limits.
     */
    private static void requireEntityExpansionLimit(XMLReader reader)
            throws SAXException {
        if (limitJdkExpansions(reader) || limitXercesExpansions(reader)) {
            return;
        }
        throw unsafeParser(reader, "cannot limit entity expansion", null);
    }

    private static boolean limitJdkExpansions(XMLReader reader) throws SAXException {
        Object current;
        try {
            current = reader.getProperty(PROPERTY_JDK_ENTITY_EXPANSION_LIMIT);
        } catch (SAXException e) {
            return false;
        }
        if (!isEffective(toLimit(current))) {
            reader.setProperty(PROPERTY_JDK_ENTITY_EXPANSION_LIMIT,
                String.valueOf(MAX_ENTITY_EXPANSIONS));
        }
        int limit = toLimit(reader.getProperty(PROPERTY_JDK_ENTITY_EXPANSION_LIMIT));
        if (!isEffective(limit)) {
            throw unsafeParser(reader, "keeps its entity expansion limit at " + limit, null);
        }
        return true;
    }

    /**
     * Apache Xerces keeps its limits in a SecurityManager. A manager whose
     * expansion limit is usable is kept. Otherwise it is replaced, not
     * changed, because the application may share it with other parsers; the
     * replacement copies every limit of the old manager and lowers only the
     * expansion limit.
     */
    private static boolean limitXercesExpansions(XMLReader reader) throws SAXException {
        Object current;
        try {
            current = reader.getProperty(PROPERTY_XERCES_SECURITY_MANAGER);
        } catch (SAXException e) {
            return false;
        }
        if (current != null && isEffective(xercesLimit(current))) {
            return true;
        }

        Object replacement = newXercesSecurityManager(reader, current);
        if (replacement == null) {
            return false;
        }
        reader.setProperty(PROPERTY_XERCES_SECURITY_MANAGER, replacement);

        Object installed = reader.getProperty(PROPERTY_XERCES_SECURITY_MANAGER);
        int limit = xercesLimit(installed);
        if (!isEffective(limit)) {
            throw unsafeParser(reader, "keeps its entity expansion limit at " + limit, null);
        }
        if (current != null && !sameLimitsExceptExpansion(current, installed)) {
            throw unsafeParser(reader, "did not keep the other limits of its"
                + " security manager", null);
        }
        return true;
    }

    /**
     * @param previous the manager to copy limits from, or null
     * @return a new manager, or null if the reader is not Apache Xerces
     */
    private static Object newXercesSecurityManager(XMLReader reader, Object previous) {
        try {
            Class managerClass = previous != null ? previous.getClass()
                : Class.forName(XERCES_SECURITY_MANAGER_CLASS, true,
                    reader.getClass().getClassLoader());
            Object manager = managerClass.newInstance();
            if (previous != null) {
                Method[] getters = intLimitGetters(managerClass);
                for (int i = 0; i < getters.length; i++) {
                    setter(managerClass, getters[i]).invoke(manager,
                        new Object[] {getters[i].invoke(previous, new Object[0])});
                }
            }
            managerClass.getMethod(SET_EXPANSION_LIMIT, new Class[] {int.class})
                .invoke(manager, new Object[] {new Integer(MAX_ENTITY_EXPANSIONS)});
            return manager;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameLimitsExceptExpansion(Object previous, Object installed) {
        if (previous.getClass() != installed.getClass()) {
            return false;
        }
        try {
            Method[] getters = intLimitGetters(previous.getClass());
            for (int i = 0; i < getters.length; i++) {
                if (getters[i].getName().equals(GET_EXPANSION_LIMIT)) {
                    continue;
                }
                Object before = getters[i].invoke(previous, new Object[0]);
                Object after = getters[i].invoke(installed, new Object[0]);
                if (!before.equals(after)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Public int getters that have a matching int setter: the limits. */
    private static Method[] intLimitGetters(Class managerClass) {
        Method[] methods = managerClass.getMethods();
        java.util.List getters = new java.util.ArrayList();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getName().startsWith("get")
                && method.getParameterTypes().length == 0
                && method.getReturnType() == int.class
                && setter(managerClass, method) != null) {
                getters.add(method);
            }
        }
        return (Method[]) getters.toArray(new Method[getters.size()]);
    }

    private static Method setter(Class managerClass, Method getter) {
        try {
            return managerClass.getMethod("set" + getter.getName().substring(3),
                new Class[] {int.class});
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static final String GET_EXPANSION_LIMIT = "getEntityExpansionLimit";

    private static final String SET_EXPANSION_LIMIT = "setEntityExpansionLimit";

    /** @return the limit of a Xerces SecurityManager, or 0 if unknown */
    private static int xercesLimit(Object manager) {
        if (manager == null) {
            return 0;
        }
        try {
            Method getter = manager.getClass().getMethod(
                GET_EXPANSION_LIMIT, new Class[0]);
            return ((Integer) getter.invoke(manager, new Object[0])).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /** @return the limit, or 0 (unlimited) if it is not a number */
    private static int toLimit(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isEffective(int limit) {
        return limit > 0 && limit <= MAX_ENTITY_EXPANSIONS;
    }

    /**
     * Install the handler that rejects external general entities and long
     * internal entities, and read it back.
     */
    private static void requireDeclarationGuard(XMLReader reader) throws SAXException {
        try {
            Object current = reader.getProperty(PROPERTY_DECLARATION_HANDLER);
            if (!(current instanceof DeclarationGuard)) {
                reader.setProperty(PROPERTY_DECLARATION_HANDLER,
                    new DeclarationGuard((DeclHandler) current));
            }
            if (reader.getProperty(PROPERTY_DECLARATION_HANDLER) instanceof DeclarationGuard) {
                return;
            }
        } catch (SAXException e) {
            throw unsafeParser(reader, "does not report entity declarations", e);
        }
        throw unsafeParser(reader, "does not keep its declaration handler", null);
    }

    /**
     * Turn XInclude off where the parser has it. A parser that does not
     * recognize the feature does not implement XInclude in the JDK and
     * Xerces; any parser resolves XInclude documents through the entity
     * resolver, which rejects them unless the application registered them.
     */
    private static void requireNoXInclude(XMLReader reader) throws SAXException {
        try {
            reader.setFeature(FEATURE_XINCLUDE, false);
        } catch (SAXNotRecognizedException e) {
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

    /**
     * Rethrows every fatal error after passing it on, so that parsing stops
     * whether or not the parser is set to continue after fatal errors.
     */
    static final class FatalErrorGuard implements ErrorHandler {

        /** Error handler set on the Digester before, or null. */
        private final ErrorHandler next;

        FatalErrorGuard(ErrorHandler next) {
            this.next = next;
        }

        public void warning(SAXParseException exception) throws SAXException {
            if (next != null) {
                next.warning(exception);
            }
        }

        public void error(SAXParseException exception) throws SAXException {
            if (next != null) {
                next.error(exception);
            }
        }

        public void fatalError(SAXParseException exception) throws SAXException {
            if (next != null) {
                next.fatalError(exception);
            }
            throw exception;
        }
    }

    /**
     * Fails on the declaration of an external general entity, and of an
     * internal entity whose replacement text is longer than
     * {@link DigesterSecurity#MAX_ENTITY_LENGTH}. Each entity expansion then
     * produces at most that many characters itself, whichever entities it
     * refers to.
     */
    private static final class DeclarationGuard implements DeclHandler {

        /** Declaration handler set on the reader before, or null. */
        private final DeclHandler next;

        DeclarationGuard(DeclHandler next) {
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
            if (value.length() > MAX_ENTITY_LENGTH) {
                throw new SAXException("Entity '" + name + "' is " + value.length()
                    + " characters long; Struts allows at most " + MAX_ENTITY_LENGTH
                    + " characters per entity.");
            }
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
