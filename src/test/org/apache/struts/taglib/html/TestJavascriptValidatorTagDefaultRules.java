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
package org.apache.struts.taglib.html;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

import junit.framework.TestCase;

import org.apache.commons.validator.ValidatorAction;
import org.apache.commons.validator.ValidatorResources;
import org.apache.struts.Globals;
import org.apache.struts.config.impl.ModuleConfigImpl;
import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockJspWriter;
import org.apache.struts.mock.MockPageContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;
import org.apache.struts.util.MessageResources;
import org.apache.struts.validator.ValidatorPlugIn;

/**
 * Tests {@link JavascriptValidatorTag} with the <code>validator-rules.xml</code>
 * distributed with Struts, whose rules name their JavaScript by resource.
 */
public class TestJavascriptValidatorTagDefaultRules extends TestCase {

    /**
     * The rules in the default <code>validator-rules.xml</code> that have
     * a JavaScript implementation.
     */
    private static final String[] CLIENT_SIDE_RULES = {
        "required",
        "minlength",
        "maxlength",
        "mask",
        "byte",
        "short",
        "integer",
        "float",
        "date",
        "intRange",
        "floatRange",
        "creditCard",
        "email",
        "includeJavaScriptUtilities",
    };

    private static final String FORM_VALIDATION_XML =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE form-validation PUBLIC\n"
        + "    \"-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.1.3//EN\"\n"
        + "    \"http://jakarta.apache.org/commons/dtds/validator_1_1_3.dtd\">\n"
        + "<form-validation>\n"
        + "  <formset>\n"
        + "    <form name=\"testForm\">\n"
        + "      <field property=\"name\" depends=\"required,maxlength\">\n"
        + "        <arg0 key=\"label.name\" />\n"
        + "        <var><var-name>maxlength</var-name><var-value>5</var-value></var>\n"
        + "      </field>\n"
        + "      <field property=\"count\" depends=\"long\">\n"
        + "        <arg0 key=\"label.count\" />\n"
        + "      </field>\n"
        + "    </form>\n"
        + "  </formset>\n"
        + "</form-validation>\n";

    private static final ValidatorResources resources = loadResources();

    private MockPageContext pageContext;

    private static ValidatorResources loadResources() {
        InputStream rules = TestJavascriptValidatorTagDefaultRules.class
            .getResourceAsStream("/org/apache/struts/resources/validator-rules.xml");
        try {
            return new ValidatorResources(new InputStream[] {
                rules,
                new ByteArrayInputStream(FORM_VALIDATION_XML.getBytes("UTF-8"))
            });
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
    }

    protected void setUp() throws Exception {
        MockServletContext context = new MockServletContext();
        context.setAttribute(Globals.MODULE_KEY, new ModuleConfigImpl(""));
        context.setAttribute(Globals.MESSAGES_KEY,
            MessageResources.getMessageResources(
                "org.apache.struts.taglib.html.EmptyBundle"));
        context.setAttribute(ValidatorPlugIn.VALIDATOR_KEY, resources);

        MockHttpServletRequest request =
            new MockHttpServletRequest(new MockHttpSession(context));
        request.setLocale(Locale.getDefault());
        pageContext = new MockPageContext(new MockServletConfig(context),
            request, new MockHttpServletResponse());
    }

    private String renderTag() throws Exception {
        MockJspWriter out = new MockJspWriter();
        pageContext.setJspWriter(out);

        JavascriptValidatorTag tag = new JavascriptValidatorTag();
        tag.setPageContext(pageContext);
        tag.setFormName("testForm");
        tag.setHtmlComment("false");
        tag.doStartTag();
        tag.doEndTag();

        return out.getContent();
    }

    public void testClientSideRules_HaveJavascript() {
        for (int i = 0; i < CLIENT_SIDE_RULES.length; i++) {
            ValidatorAction action = resources.getValidatorAction(CLIENT_SIDE_RULES[i]);
            assertNotNull(CLIENT_SIDE_RULES[i], action);
            String javascript = action.getJavascript();
            assertNotNull(CLIENT_SIDE_RULES[i], javascript);
            assertTrue(CLIENT_SIDE_RULES[i], javascript.trim().length() > 0);
        }
    }

    public void testFormFunction_CallsClientSideRules() throws Exception {
        String output = renderTag();
        assertTrue(output, output.indexOf(
            "formValidationResult = validateRequired(form) && validateMaxLength(form);") >= 0);
    }

    public void testFormFunction_ServerSideRule_IsNotCalled() throws Exception {
        String output = renderTag();
        assertTrue(output, output.indexOf("validateLong") < 0);
    }

    public void testStaticJavascript_DefinesRuleFunctions() throws Exception {
        String output = renderTag();
        assertTrue(output, output.indexOf("function validateRequired(form)") >= 0);
        assertTrue(output, output.indexOf("function validateMaxLength(form)") >= 0);
    }
}
