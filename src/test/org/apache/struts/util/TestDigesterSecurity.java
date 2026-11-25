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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Tests for {@link DigesterSecurity}: a secured Digester accepts a DOCTYPE,
 * reads external DTDs and entities only from sources that trusted code
 * provided, and keeps validating as configured.
 */
public class TestDigesterSecurity extends TestCase {

    private static final String TEST_PUBLIC_ID = "-//Struts1 Forever//DTD Test Root//EN";

    /** Local copy of the test DTD; written once for all tests. */
    private static String dtdUrl;

    private ConnectionCounter counter;
    private int connectionsBefore;

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        if (dtdUrl == null) {
            dtdUrl = writeTempFile("<!ELEMENT root (child*)>\n<!ELEMENT child EMPTY>\n");
        }
    }

    private void assertNoConnection() {
        assertEquals("connections to the system ID",
            connectionsBefore, counter.getCount());
    }

    // ------------------------------------------------------------------
    // Registered DTDs
    // ------------------------------------------------------------------

    public void testRegisteredPublicId_ValidatingParser_UsesLocalDtd() throws Exception {
        assertRegisteredPublicIdIsResolvedLocally(true);
    }

    public void testRegisteredPublicId_NonValidatingParser_UsesLocalDtd() throws Exception {
        assertRegisteredPublicIdIsResolvedLocally(false);
    }

    private void assertRegisteredPublicIdIsResolvedLocally(boolean validating)
            throws Exception {
        Digester digester = newDigester(validating);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        ErrorCollector errors = collectErrors(digester);
        DigesterSecurity.secure(digester);

        digester.parse(stream(
            "<!DOCTYPE root PUBLIC \"" + TEST_PUBLIC_ID + "\" \""
            + counter.url("/root.dtd") + "\">\n<root><child/></root>"));

        assertNoConnection();
        assertEquals(0, errors.errors.size());
    }

    public void testRegisteredPublicId_IsReportedByGetPublicIdDuringParse()
            throws Exception {
        final Digester digester = newDigester(false);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        final List seen = new ArrayList();
        digester.addRule("root", new Rule() {
            public void begin(Attributes attributes) {
                seen.add(digester.getPublicId());
            }
        });
        DigesterSecurity.secure(digester);

        digester.parse(stream(
            "<!DOCTYPE root PUBLIC \"" + TEST_PUBLIC_ID + "\" \"root.dtd\">\n<root/>"));

        assertEquals(1, seen.size());
        assertEquals(TEST_PUBLIC_ID, seen.get(0));
    }

    public void testSystemIdOnly_RegisteredBySystemId_UsesLocalDtd() throws Exception {
        String systemId = counter.url("/root.dtd");
        Digester digester = newDigester(false);
        digester.register(systemId, dtdUrl);
        DigesterSecurity.secure(digester);

        digester.parse(stream("<!DOCTYPE root SYSTEM \"" + systemId + "\">\n<root/>"));

        assertNoConnection();
    }

    public void testRegistrationAddedAfterSecure_IsUsed() throws Exception {
        Digester digester = newDigester(false);
        DigesterSecurity.secure(digester);
        digester.register(TEST_PUBLIC_ID, dtdUrl);

        digester.parse(stream(
            "<!DOCTYPE root PUBLIC \"" + TEST_PUBLIC_ID + "\" \"root.dtd\">\n<root/>"));
    }

    // ------------------------------------------------------------------
    // Parser configuration made before secure() is kept
    // ------------------------------------------------------------------

    public void testValidatingSetBeforeSecure_ReportsValidationError() throws Exception {
        Digester digester = newDigester(true);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        ErrorCollector errors = collectErrors(digester);
        DigesterSecurity.secure(digester);

        digester.parse(stream(
            "<!DOCTYPE root PUBLIC \"" + TEST_PUBLIC_ID + "\" \"root.dtd\">\n"
            + "<root><undeclared/></root>"));

        assertTrue("validation error reported", errors.errors.size() > 0);
    }

    public void testNamespaceAwareSetBeforeSecure_IsHonoured() throws Exception {
        Digester digester = new Digester();
        digester.setNamespaceAware(true);
        DigesterSecurity.secure(digester);

        assertTrue(digester.getXMLReader().getFeature(
            "http://xml.org/sax/features/namespaces"));
    }

    // ------------------------------------------------------------------
    // Unregistered external resources are rejected before any access
    // ------------------------------------------------------------------

    public void testUnregisteredPublicId_ValidatingParser_IsRejectedWithoutConnection()
            throws Exception {
        assertUnregisteredPublicIdIsRejected(true);
    }

    public void testUnregisteredPublicId_NonValidatingParser_IsRejectedWithoutConnection()
            throws Exception {
        assertUnregisteredPublicIdIsRejected(false);
    }

    private void assertUnregisteredPublicIdIsRejected(boolean validating)
            throws Exception {
        Digester digester = newDigester(validating);
        DigesterSecurity.secure(digester);

        assertRejected(digester,
            "<!DOCTYPE root PUBLIC \"-//Unknown//DTD Unknown//EN\" \""
            + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testUnregisteredSystemIdOnly_IsRejectedWithoutConnection()
            throws Exception {
        Digester digester = newDigester(false);
        DigesterSecurity.secure(digester);

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testSecuredTwice_SecondParse_IsStillRestricted() throws Exception {
        Digester digester = newDigester(false);
        DigesterSecurity.secure(digester);
        digester.parse(stream("<root/>"));
        DigesterSecurity.secure(digester);

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testExternalGeneralEntity_IsNotFetched() throws Exception {
        Digester digester = newDigester(false);
        DigesterSecurity.secure(digester);

        parseIgnoringRejection(digester,
            "<!DOCTYPE root [\n"
            + "  <!ENTITY xxe SYSTEM \"" + counter.url("/entity") + "\">\n"
            + "]>\n<root>&xxe;</root>");

        assertNoConnection();
    }

    public void testExternalParameterEntity_IsNotFetched() throws Exception {
        Digester digester = newDigester(true);
        DigesterSecurity.secure(digester);

        parseIgnoringRejection(digester,
            "<!DOCTYPE root [\n"
            + "  <!ENTITY % ext SYSTEM \"" + counter.url("/param.dtd") + "\">\n"
            + "  %ext;\n"
            + "]>\n<root/>");

        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // Extension points
    // ------------------------------------------------------------------

    public void testInstalledEntityResolver_IsUsedFirst() throws Exception {
        final List requested = new ArrayList();
        Digester digester = newDigester(false);
        digester.setEntityResolver(new EntityResolver() {
            public InputSource resolveEntity(String publicId, String systemId) {
                requested.add(systemId);
                return new InputSource(dtdUrl);
            }
        });
        DigesterSecurity.secure(digester);

        digester.parse(stream(
            "<!DOCTYPE root SYSTEM \"" + counter.url("/custom.dtd") + "\">\n<root/>"));

        assertEquals(1, requested.size());
        assertNoConnection();
    }

    public void testInstalledEntityResolver_ReturningNull_IsRejected() throws Exception {
        Digester digester = newDigester(false);
        digester.setEntityResolver(new EntityResolver() {
            public InputSource resolveEntity(String publicId, String systemId) {
                return null;
            }
        });
        DigesterSecurity.secure(digester);

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testDigesterCreatedFromXmlReader_IsRestricted() throws Exception {
        Digester digester = new Digester(newXmlReader());
        DigesterSecurity.secure(digester);

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testReaderThatCannotDisableExternalEntities_FailsToSecure()
            throws Exception {
        Digester digester = new Digester(
            new FeatureRefusingReader(newXmlReader(),
                "http://xml.org/sax/features/external-general-entities"));

        try {
            DigesterSecurity.secure(digester);
            fail("a reader that keeps external entities enabled must not be used");
        } catch (SAXException e) {
            // expected
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Digester newDigester(boolean validating) {
        Digester digester = new Digester();
        digester.setValidating(validating);
        return digester;
    }

    private static XMLReader newXmlReader() throws Exception {
        return SAXParserFactory.newInstance().newSAXParser().getXMLReader();
    }

    private static ErrorCollector collectErrors(Digester digester) {
        ErrorCollector errors = new ErrorCollector();
        digester.setErrorHandler(errors);
        return errors;
    }

    private static void assertRejected(Digester digester, String xml)
            throws IOException {
        try {
            digester.parse(stream(xml));
            fail("document must be rejected");
        } catch (SAXException e) {
            // expected
        }
    }

    /** Rejecting the document is acceptable; reading the entity is not. */
    private static void parseIgnoringRejection(Digester digester, String xml)
            throws IOException {
        try {
            digester.parse(stream(xml));
        } catch (SAXException e) {
            // either outcome is fine
        }
    }

    private static ByteArrayInputStream stream(String xml) throws IOException {
        return new ByteArrayInputStream(
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xml).getBytes("UTF-8"));
    }

    private static String writeTempFile(String content) throws IOException {
        File file = File.createTempFile("root", ".dtd");
        file.deleteOnExit();
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return file.toURL().toString();
    }

    /** Records recoverable errors, such as validation errors. */
    private static class ErrorCollector implements ErrorHandler {
        final List errors = new ArrayList();

        public void warning(SAXParseException e) {
        }

        public void error(SAXParseException e) {
            errors.add(e);
        }

        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }

    /** XMLReader that refuses to change one feature. */
    private static class FeatureRefusingReader implements XMLReader {
        private final XMLReader delegate;
        private final String refused;

        FeatureRefusingReader(XMLReader delegate, String refused) {
            this.delegate = delegate;
            this.refused = refused;
        }

        public boolean getFeature(String name)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getFeature(name);
        }

        public void setFeature(String name, boolean value)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            if (refused.equals(name)) {
                throw new SAXNotSupportedException(name);
            }
            delegate.setFeature(name, value);
        }

        public Object getProperty(String name)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getProperty(name);
        }

        public void setProperty(String name, Object value)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setProperty(name, value);
        }

        public void setEntityResolver(EntityResolver resolver) {
            delegate.setEntityResolver(resolver);
        }

        public EntityResolver getEntityResolver() {
            return delegate.getEntityResolver();
        }

        public void setDTDHandler(DTDHandler handler) {
            delegate.setDTDHandler(handler);
        }

        public DTDHandler getDTDHandler() {
            return delegate.getDTDHandler();
        }

        public void setContentHandler(ContentHandler handler) {
            delegate.setContentHandler(handler);
        }

        public ContentHandler getContentHandler() {
            return delegate.getContentHandler();
        }

        public void setErrorHandler(ErrorHandler handler) {
            delegate.setErrorHandler(handler);
        }

        public ErrorHandler getErrorHandler() {
            return delegate.getErrorHandler();
        }

        public void parse(InputSource input) throws IOException, SAXException {
            delegate.parse(input);
        }

        public void parse(String systemId) throws IOException, SAXException {
            delegate.parse(systemId);
        }
    }
}
