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
package org.apache.struts.tiles.xmlDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.struts.util.ConnectionCounter;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Tiles definition files may declare the Tiles DTD, are validated when the
 * parser is set to validate, and cannot make the parser read an
 * unregistered external resource (issue #3).
 */
public class TestXmlParser extends TestCase {

    private static final String TILES_11 =
        "-//Apache Software Foundation//DTD Tiles Configuration 1.1//EN";

    private ConnectionCounter counter;
    private int connectionsBefore;

    /** Exposes the parse errors that the Digester reports. */
    private static class ErrorCollectingParser extends XmlParser {
        final List errors = new ArrayList();

        ErrorCollectingParser() {
            digester.setErrorHandler(new ErrorHandler() {
                public void warning(SAXParseException e) {
                }

                public void error(SAXParseException e) {
                    errors.add(e);
                }

                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
        }
    }

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
    }

    public void testTiles11Doctype_IsParsedWithoutConnection() throws Exception {
        XmlParser parser = new XmlParser();
        XmlDefinitionsSet definitions = new XmlDefinitionsSet();

        parser.parse(stream(doctype(TILES_11)
            + "<tiles-definitions><definition name=\"main\" path=\"/main.jsp\"/>"
            + "</tiles-definitions>"), definitions);

        assertNotNull(definitions.getDefinition("main"));
        assertEquals(connectionsBefore, counter.getCount());
    }

    /**
     * {@code setValidating(true)} is called after the constructor, as
     * {@code I18nFactorySet} does, and still takes effect.
     */
    public void testSetValidatingAfterConstruction_ReportsValidationError()
            throws Exception {
        ErrorCollectingParser parser = new ErrorCollectingParser();
        parser.setValidating(true);

        parser.parse(stream(doctype(TILES_11)
            + "<tiles-definitions><undeclared/></tiles-definitions>"),
            new XmlDefinitionsSet());

        assertTrue("validation error reported", parser.errors.size() > 0);
    }

    public void testUnregisteredDtd_IsRejectedWithoutConnection() throws Exception {
        try {
            new XmlParser().parse(stream(doctype("-//Unknown//DTD Unknown//EN")
                + "<tiles-definitions/>"), new XmlDefinitionsSet());
            fail("definitions file with an unregistered DTD must be rejected");
        } catch (SAXException e) {
            // expected
        }
        assertEquals(connectionsBefore, counter.getCount());
    }

    private String doctype(String publicId) {
        return "<!DOCTYPE tiles-definitions PUBLIC \"" + publicId + "\" \""
            + counter.url("/remote.dtd") + "\">\n";
    }

    private static ByteArrayInputStream stream(String xml) throws IOException {
        return new ByteArrayInputStream(
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xml).getBytes("UTF-8"));
    }
}
