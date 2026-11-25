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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import javax.servlet.ServletException;

import junit.framework.TestCase;

import org.apache.commons.validator.Arg;
import org.apache.commons.validator.Form;
import org.apache.commons.validator.ValidatorAction;
import org.apache.commons.validator.ValidatorResources;
import org.apache.struts.config.ModuleConfigFactory;
import org.apache.struts.mock.MemoryServletContext;
import org.apache.struts.mock.MockActionServlet;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.util.ConnectionCounter;
import org.xml.sax.SAXException;

/**
 * {@link ValidatorResourcesLoader} loads validation files exactly as
 * <code>new ValidatorResources(InputStream[])</code> of the Commons Validator
 * on the class path does, but cannot be made to read an unregistered external
 * resource. {@code TestOldCommonsLibraries} also runs it with Commons
 * Validator 1.1.4.
 */
public class TestValidatorResourcesLoader extends TestCase {

    private static final String RULES = "/org/apache/struts/resources/validator-rules.xml";

    /** arg0-arg1 elements, handled by the rules that the loader adds itself. */
    private static final String OLD_STYLE =
        "<!DOCTYPE form-validation PUBLIC \"-//Apache Software Foundation//DTD"
        + " Commons Validator Rules Configuration 1.1.3//EN\" \"validator_1_1_3.dtd\">\n"
        + "<form-validation>\n"
        + "  <global><constant><constant-name>zip</constant-name>"
        + "<constant-value>^\\d{5}$</constant-value></constant></global>\n"
        + "  <formset><form name=\"testForm\">\n"
        + "    <field property=\"name\" depends=\"required,maxlength\">\n"
        + "      <arg0 key=\"name.label\"/>\n"
        + "      <arg1 name=\"maxlength\" key=\"${var:maxlength}\" resource=\"false\"/>\n"
        + "      <var><var-name>maxlength</var-name><var-value>10</var-value></var>\n"
        + "    </field>\n"
        + "    <field property=\"zip\" depends=\"mask\">\n"
        + "      <msg name=\"mask\" key=\"zip.invalid\"/>\n"
        + "      <arg0 key=\"zip.label\"/>\n"
        + "      <var><var-name>mask</var-name><var-value>${zip}</var-value></var>\n"
        + "    </field>\n"
        + "  </form></formset>\n"
        + "</form-validation>";

    /** arg elements with a position, handled by the Commons Validator rules. */
    private static final String NEW_STYLE =
        "<!DOCTYPE form-validation PUBLIC \"-//Apache Software Foundation//DTD"
        + " Commons Validator Rules Configuration 1.2.0//EN\" \"validator_1_2_0.dtd\">\n"
        + "<form-validation><formset language=\"fr\"><form name=\"testForm\">\n"
        + "  <field property=\"name\" depends=\"required\">"
        + "<arg key=\"name.label.fr\" position=\"0\"/></field>\n"
        + "</form></formset></form-validation>";

    private ConnectionCounter counter;
    private int connectionsBefore;

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
    }

    public void testLoad_GivesSameResourcesAsCommonsValidator() throws Exception {
        ValidatorResources expected = new ValidatorResources(streams());
        ValidatorResources actual = ValidatorResourcesLoader.load(streams());

        String description = describe(expected);
        assertTrue(description.indexOf("name.label") >= 0);
        assertTrue(description.indexOf("^\\d{5}$") >= 0);
        assertEquals(description, describe(actual));
    }

    /**
     * The loader registers a DTD exactly where Commons Validator does;
     * otherwise Commons Validator would fetch a DTD the check allowed.
     */
    public void testRegistrations_MatchCommonsValidator() throws Exception {
        Field field;
        try {
            field = ValidatorResources.class.getDeclaredField("REGISTRATIONS");
        } catch (NoSuchFieldException e) {
            field = ValidatorResources.class.getDeclaredField("registrations");
        }
        field.setAccessible(true);

        assertEquals(Arrays.asList((String[]) field.get(null)),
            shippedRegistrations());
    }

    /** Control: Commons Validator itself fetches an external parameter entity. */
    public void testExternalParameterEntity_CommonsValidator_Fetches() throws Exception {
        try {
            new ValidatorResources(new InputStream[] {stream(externalParameterEntity())});
        } catch (SAXException e) {
            // the control only counts connections
        }
        assertEquals(connectionsBefore + 1, counter.getCount());
    }

    public void testExternalParameterEntity_IsRejectedWithoutConnection() throws Exception {
        assertRejected(externalParameterEntity());
        assertEquals(connectionsBefore, counter.getCount());
    }

    public void testUnregisteredDtd_IsRejectedWithoutConnection() throws Exception {
        assertRejected("<!DOCTYPE form-validation PUBLIC \"-//Unknown//DTD Unknown//EN\" \""
            + counter.url("/unknown.dtd") + "\">\n<form-validation/>");
        assertEquals(connectionsBefore, counter.getCount());
    }

    public void testExternalEntity_IsRejectedWithoutConnection() throws Exception {
        assertRejected("<!DOCTYPE form-validation [\n"
            + "  <!ENTITY xxe SYSTEM \"" + counter.url("/entity") + "\">\n"
            + "]>\n<form-validation>&xxe;</form-validation>");
        assertEquals(connectionsBefore, counter.getCount());
    }

    public void testPlugIn_UnregisteredDtd_FailsWithoutConnection() throws Exception {
        MemoryServletContext context = new MemoryServletContext();
        context.put("/WEB-INF/validation.xml", "<!DOCTYPE form-validation PUBLIC"
            + " \"-//Unknown//DTD Unknown//EN\" \"" + counter.url("/unknown.dtd")
            + "\">\n<form-validation/>");
        ValidatorPlugIn plugIn = new ValidatorPlugIn();
        plugIn.setPathnames("/WEB-INF/validation.xml");

        try {
            plugIn.init(new MockActionServlet(context, new MockServletConfig(context)),
                ModuleConfigFactory.createFactory().createModuleConfig(""));
            fail("validation file with an unregistered DTD must be rejected");
        } catch (ServletException e) {
            // expected
        }
        assertEquals(connectionsBefore, counter.getCount());
    }

    private void assertRejected(String xml) throws IOException {
        try {
            ValidatorResourcesLoader.load(new InputStream[] {stream(xml)});
            fail("document must be rejected");
        } catch (SAXException e) {
            // expected
        }
    }

    private String externalParameterEntity() {
        return "<!DOCTYPE form-validation [\n"
            + "  <!ENTITY % ext SYSTEM \"" + counter.url("/param.dtd") + "\">\n"
            + "  %ext;\n"
            + "]>\n<form-validation/>";
    }

    /** The loader's registrations whose DTD this Commons Validator ships. */
    private static List shippedRegistrations() {
        List shipped = new ArrayList();
        for (int i = 0; i < ValidatorResourcesLoader.REGISTRATIONS.length; i += 2) {
            if (ValidatorResources.class.getResource(
                    ValidatorResourcesLoader.REGISTRATIONS[i + 1]) != null) {
                shipped.add(ValidatorResourcesLoader.REGISTRATIONS[i]);
                shipped.add(ValidatorResourcesLoader.REGISTRATIONS[i + 1]);
            }
        }
        return shipped;
    }

    /** The 1.2.0 file is left out where Commons Validator lacks that DTD. */
    private InputStream[] streams() throws IOException {
        if (ValidatorResources.class.getResource(
                "/org/apache/commons/validator/resources/validator_1_2_0.dtd") == null) {
            return new InputStream[] {
                getClass().getResourceAsStream(RULES), stream(OLD_STYLE)
            };
        }
        return new InputStream[] {
            getClass().getResourceAsStream(RULES), stream(OLD_STYLE), stream(NEW_STYLE)
        };
    }

    private static InputStream stream(String xml) throws IOException {
        return new ByteArrayInputStream(
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xml).getBytes("UTF-8"));
    }

    /** Text that differs when the loaded actions, forms, fields or args differ. */
    private static String describe(ValidatorResources resources) {
        StringBuffer text = new StringBuffer();
        TreeMap actions = new TreeMap(resources.getValidatorActions());
        for (Iterator i = actions.values().iterator(); i.hasNext();) {
            ValidatorAction action = (ValidatorAction) i.next();
            text.append(action.getName()).append(' ').append(action.getClassname())
                .append(' ').append(action.getMethod()).append(' ')
                .append(action.getMethodParams()).append(' ').append(action.getDepends())
                .append(' ').append(action.getMsg()).append('\n');
        }
        describeForm(text, resources.getForm(Locale.US, "testForm"));
        describeForm(text, resources.getForm(Locale.FRENCH, "testForm"));
        return text.toString();
    }

    private static void describeForm(StringBuffer text, Form form) {
        text.append(form);
        for (Iterator i = form.getFields().iterator(); i.hasNext();) {
            org.apache.commons.validator.Field field =
                (org.apache.commons.validator.Field) i.next();
            for (int position = 0; position < 4; position++) {
                Arg arg = field.getArg(position);
                text.append(field.getProperty()).append(" arg").append(position)
                    .append(": ").append(arg);
            }
            text.append(field.getProperty()).append(" msg: ")
                .append(field.getMsg("mask")).append('\n');
        }
    }
}
