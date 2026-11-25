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

import javax.servlet.jsp.PageContext;

import junit.framework.TestCase;

import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockJspWriter;
import org.apache.struts.mock.MockPageContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;

/**
 * Attribute values written by {@link BaseHandlerTag#prepareAttribute} cannot
 * end their attribute (M-4): <code>"</code>, <code>'</code>, <code>&lt;</code>
 * and <code>&gt;</code> are escaped.
 *
 * <p>This keeps user data from adding attributes or elements. It does not make
 * data safe inside an event handler. The handler is JavaScript in an HTML
 * attribute, and the browser decodes the attribute before running it, so the
 * application escapes data for a JavaScript string literal and then escapes
 * the code it built with <code>TagUtils.filter()</code>. Character references
 * in values are kept (issue #4), so the browser runs that code as built; see
 * {@link TestAttributeValueEncoding}.</p>
 */
public class TestBaseHandlerTagXss extends TestCase {

    private MockServletContext context;
    private MockServletConfig config;
    private MockHttpSession session;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockPageContext pageContext;
    private MockJspWriter out;

    protected void setUp() {
        context = new MockServletContext();
        config = new MockServletConfig(context);
        session = new MockHttpSession(context);
        request = new MockHttpServletRequest(session);
        response = new MockHttpServletResponse();
        pageContext = new MockPageContext(config, request, response);
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
    }

    // ------------------------------------------------------------------
    // Helper: run a TextTag with a given onclick value
    // ------------------------------------------------------------------

    /**
     * Stores {@code beanValue} as a request attribute named "testBean",
     * sets up a TextTag, sets its onclick to {@code onclickValue},
     * calls doStartTag(), and returns the rendered output.
     */
    private String renderTextTagWithOnclick(String onclickValue) throws Exception {
        pageContext.setAttribute("testBean", "someValue", PageContext.REQUEST_SCOPE);

        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        TextTag tag = new TextTag();
        tag.setPageContext(pageContext);
        tag.setName("testBean");
        tag.setProperty("class");
        tag.setValue("someValue");
        tag.setOnclick(onclickValue);

        tag.doStartTag();
        tag.doEndTag();

        return out.getContent();
    }

    // ------------------------------------------------------------------
    // onclick (M-4)
    // ------------------------------------------------------------------

    /**
     * A double-quote in an onclick value must be escaped as {@code &quot;}
     * to prevent breaking out of the attribute context (XSS).
     */
    public void testOnclick_EscapesDoubleQuote() throws Exception {
        String output = renderTextTagWithOnclick("bad\"injection");
        assertFalse("Raw double-quote in onclick enables XSS (M-4)",
            output.contains("onclick=\"bad\"injection\""));
        assertTrue("&quot; must appear instead of raw \"",
            output.contains("&quot;"));
    }

    /**
     * A single-quote in an onclick value must be escaped as {@code &#39;}.
     */
    public void testOnclick_EscapesSingleQuote() throws Exception {
        String output = renderTextTagWithOnclick("bad'injection");
        assertFalse("Raw single-quote in onclick enables XSS (M-4)",
            output.contains("onclick=\"bad'injection\""));
        assertTrue("&#39; must appear", output.contains("&#39;"));
    }

    /**
     * A less-than sign in an onclick value must be escaped as {@code &lt;}.
     */
    public void testOnclick_EscapesLessThan() throws Exception {
        String output = renderTextTagWithOnclick("a<b");
        assertFalse("Raw < in onclick enables HTML injection (M-4)",
            output.contains("onclick=\"a<b\""));
        assertTrue("&lt; must appear", output.contains("&lt;"));
    }

    // ------------------------------------------------------------------
    // value attribute (prepareValue) — correctly escaped via formatValue()
    // ------------------------------------------------------------------

    /**
     * The value attribute IS correctly escaped via
     * {@code BaseFieldTag.formatValue()} → {@code TagUtils.filter()}.
     * This guards against accidental removal of that escaping in future refactoring.
     */
    public void testValue_EscapesHtmlSpecialChars() throws Exception {
        pageContext.setAttribute("testBean", "xss", PageContext.REQUEST_SCOPE);
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        TextTag tag = new TextTag();
        tag.setPageContext(pageContext);
        tag.setName("testBean");
        tag.setProperty("class");
        tag.setValue("<script>alert(1)</script>");

        tag.doStartTag();
        tag.doEndTag();

        String output = out.getContent();
        assertFalse("Raw <script> must not appear in value attribute",
                output.contains("value=\"<script>"));
        assertTrue("value attribute must contain escaped &lt;script&gt;",
                output.contains("&lt;script&gt;"));
    }

    // ------------------------------------------------------------------
    // tabindex — also goes through prepareAttribute (M-4)
    // ------------------------------------------------------------------

    /**
     * A double-quote in tabindex must be escaped as {@code &quot;}
     * to prevent attribute injection.
     */
    public void testTabindex_EscapesDoubleQuote() throws Exception {
        pageContext.setAttribute("testBean", "someValue", PageContext.REQUEST_SCOPE);
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        TextTag tag = new TextTag();
        tag.setPageContext(pageContext);
        tag.setName("testBean");
        tag.setProperty("class");
        tag.setValue("someValue");
        tag.setTabindex("1\" autofocus onfocus=\"alert(1)");

        tag.doStartTag();
        tag.doEndTag();

        String output = out.getContent();
        assertFalse("Raw injection payload must not reach output (M-4)",
            output.contains("tabindex=\"1\" autofocus"));
        assertTrue("&quot; must appear in tabindex value",
            output.contains("&quot;"));
    }
}
