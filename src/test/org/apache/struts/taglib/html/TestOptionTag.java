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

import java.util.Locale;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockJspWriter;
import org.apache.struts.mock.MockPageContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;
import org.apache.struts.taglib.bean.WriteTag;
import org.apache.struts.util.MessageResources;

/**
 * Tests for {@link OptionTag} HTML escaping (H-3 fix).
 *
 * <p>With {@code filter} {@code true} (the default) the value, which is data,
 * is escaped with {@link org.apache.struts.taglib.TagUtils#filter(String)} as
 * in {@code <html:options>}; the label, which is HTML, keeps its character
 * references and only {@code " ' < >} are escaped (issue #4).</p>
 *
 * <p>Body text is written into the body content that
 * {@link MockPageContext#pushBody()} returns and read by
 * {@code doAfterBody()}, as in a JSP container.</p>
 */
public class TestOptionTag extends TestCase {

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
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Creates a SelectTag with {@code match=[]} (no pre-selected value)
     * and stores it as the SELECT_KEY page attribute, simulating the tag
     * being nested inside a {@code <html:select>}.
     */
    private SelectTag stubSelectTag() {
        SelectTag select = new SelectTag();
        select.setPageContext(pageContext);
        select.match = new String[0];
        pageContext.setAttribute(Constants.SELECT_KEY, select,
                PageContext.PAGE_SCOPE);
        return select;
    }

    /** Writes part of the body of a tag, as the JSP page would. */
    private interface Body {
        void write() throws Exception;
    }

    /** A body that consists of the given template text. */
    private Body text(final String text) {
        return new Body() {
            public void write() throws Exception {
                pageContext.getOut().print(text);
            }
        };
    }

    /**
     * Runs the tag lifecycle as a JSP container does for a tag that
     * returns {@code EVAL_BODY_TAG}; a {@code null} body is an empty tag.
     */
    private void evaluate(OptionTag tag, Body body) throws Exception {
        assertEquals(OptionTag.EVAL_BODY_TAG, tag.doStartTag());
        if (body != null) {
            BodyContent content = pageContext.pushBody();
            tag.setBodyContent(content);
            tag.doInitBody();
            body.write();
            tag.doAfterBody();
            pageContext.popBody();
        }
        tag.doEndTag();
    }

    /**
     * Runs OptionTag with the given value and optional body text and
     * returns the captured output.
     */
    private String runOptionTag(String value, String text) throws Exception {
        stubSelectTag();
        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue(value);
        evaluate(tag, text == null ? null : text(text));
        return out.getContent();
    }

    private String runOptionTagSelecting(String value, String selected) throws Exception {
        stubSelectTag().match = new String[] {selected};
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue(value);
        evaluate(tag, text("Label"));
        return out.getContent();
    }

    /**
     * The label of the option element as the browser reads it. The start
     * tag ends at the first {@code >} outside a quoted attribute value, and
     * the text ends at the next {@code <}, which must start
     * {@code </option>}: a label that starts an element fails here instead
     * of being decoded.
     */
    private static String label(String html) {
        int start = html.indexOf("<option");
        assertTrue(html, start >= 0);
        boolean quoted = false;
        int i = start;
        while (i < html.length() && (quoted || html.charAt(i) != '>')) {
            if (html.charAt(i) == '"') {
                quoted = !quoted;
            }
            i++;
        }
        assertTrue("unterminated start tag: " + html, i < html.length());
        int textStart = i + 1;
        int textEnd = html.indexOf('<', textStart);
        assertTrue(html, textEnd >= 0);
        assertTrue("label starts markup: " + html,
                html.startsWith("</option>", textEnd));
        return TestAttributeValueEncoding.decode(html.substring(textStart, textEnd));
    }

    /** Returns the same message for every key. */
    private static class FixedMessageResources extends MessageResources {
        private final String message;

        FixedMessageResources(String message) {
            super(null, null);
            this.message = message;
        }

        public String getMessage(Locale locale, String key) {
            return message;
        }
    }

    private OptionTag optionTagWithKey(String value, String message) {
        pageContext.setAttribute(Globals.MESSAGES_KEY,
                new FixedMessageResources(message),
                PageContext.REQUEST_SCOPE);
        stubSelectTag();
        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue(value);
        tag.setKey("typeForm.option.select.one");
        return tag;
    }

    // ------------------------------------------------------------------
    // Normal rendering
    // ------------------------------------------------------------------

    public void testOption_PlainValueAndText_RenderedCorrectly() throws Exception {
        String output = runOptionTag("foo", "Foo Label");
        assertTrue("value attribute must appear", output.contains("value=\"foo\""));
        assertTrue("label text must appear", output.contains("Foo Label"));
        assertTrue("must be wrapped in <option>", output.contains("<option"));
        assertTrue("must close with </option>", output.contains("</option>"));
    }

    public void testOption_NoText_UsesValueAsLabel() throws Exception {
        String output = runOptionTag("myval", null);
        assertTrue("When no text is set, value is used as label",
                output.contains(">myval<"));
    }

    /** Surrounding white space of the body is not part of the label. */
    public void testText_IsTrimmed() throws Exception {
        assertEquals("Label", label(runOptionTag("v", "\n  Label\n")));
    }

    /** A body of white space only shows the value, as an empty tag does. */
    public void testText_OnlyWhiteSpace_ShowsTheValue() throws Exception {
        assertEquals("v", label(runOptionTag("v", " \n ")));
    }

    /** The body of one use of a tag instance does not show in the next. */
    public void testText_IsNotReusedByTheNextEvaluation() throws Exception {
        stubSelectTag();
        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue("v");
        evaluate(tag, text("First"));
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
        evaluate(tag, null);
        assertEquals("v", label(out.getContent()));
    }

    // ------------------------------------------------------------------
    // value attribute — must be HTML-escaped (H-3)
    // ------------------------------------------------------------------

    /**
     * A double-quote in the value attribute must be escaped as {@code &quot;}
     * to prevent breaking out of the attribute context (H-3).
     */
    public void testValueAttribute_EscapesDoubleQuote() throws Exception {
        String output = runOptionTag("a\"b", "Label");
        assertEquals("a\"b", TestAttributeValueEncoding.attribute(output, "value"));
        assertEquals("Label", label(output));
    }

    /**
     * A less-than sign in the value attribute must be escaped as {@code &lt;}
     * to prevent HTML injection (H-3).
     */
    public void testValueAttribute_EscapesLessThan() throws Exception {
        String output = runOptionTag("<script>", "Label");
        assertFalse("Raw <script> in value attribute must be escaped (H-3)",
            output.contains("value=\"<script>\""));
        assertTrue("&lt;script&gt; must appear", output.contains("&lt;script&gt;"));
    }

    // ------------------------------------------------------------------
    // text content — must be HTML-escaped (H-3)
    // ------------------------------------------------------------------

    /**
     * A script tag in the option text content must be HTML-escaped (H-3).
     */
    public void testText_EscapesScriptTag() throws Exception {
        String output = runOptionTag("v", "<script>alert(1)</script>");
        assertEquals("<script>alert(1)</script>", label(output));
    }

    /**
     * A single-quote in option text must be escaped as {@code &#39;} (H-3).
     */
    public void testText_EscapesSingleQuote() throws Exception {
        String output = runOptionTag("v", "it's");
        assertFalse("Raw single-quote in option text must be escaped (H-3)",
            output.contains(">it's<"));
        assertTrue("&#39; must appear", output.contains("&#39;"));
    }

    // ------------------------------------------------------------------
    // Character references — HTML labels and data values (issues #4, #5)
    // ------------------------------------------------------------------

    /** &nbsp; and &eacute; display as they did in 1.2.9-forever-2. */
    public void testText_WithCharacterReferences_IsKept() throws Exception {
        String output = runOptionTag("child", "&nbsp;&nbsp;Caf&eacute;");
        assertTrue(output, output.contains(">&nbsp;&nbsp;Caf&eacute;</option>"));
    }

    /** A blank option written as {@code &nbsp;} stays blank. */
    public void testText_BlankOptionWithNbsp_IsKept() throws Exception {
        String output = runOptionTag("", "&nbsp;");
        assertTrue(output, output.contains(">&nbsp;</option>"));
    }

    /**
     * {@code <bean:write>} nested in the body escapes its output, and the
     * option shows that output as {@code <bean:write>} alone would.
     */
    public void testText_FromNestedWriteTag_IsEscapedOnce() throws Exception {
        pageContext.setAttribute("member", "Tom & Jerry <TJ>",
                PageContext.PAGE_SCOPE);
        stubSelectTag();
        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue("1");

        evaluate(tag, new Body() {
            public void write() throws Exception {
                WriteTag write = new WriteTag();
                write.setPageContext(pageContext);
                write.setName("member");
                write.doStartTag();
                write.doEndTag();
            }
        });

        String output = out.getContent();
        assertTrue(output,
                output.contains(">Tom &amp; Jerry &lt;TJ&gt;</option>"));
        assertEquals("Tom & Jerry <TJ>", label(output));
    }

    /**
     * Message resources hold HTML because {@code <bean:message>} writes them
     * as given, so a label looked up by key keeps its character references.
     */
    public void testLabel_FromKey_KeepsCharacterReferences() throws Exception {
        OptionTag tag = optionTagWithKey("", "&lt;Select One&gt;&nbsp;");
        evaluate(tag, null);
        assertTrue(out.getContent(),
                out.getContent().contains(">&lt;Select One&gt;&nbsp;</option>"));
    }

    /** A message looked up by key cannot add elements to the page. */
    public void testLabel_FromKey_EscapesMarkup() throws Exception {
        OptionTag tag = optionTagWithKey("v", "</select><script>x()</script>");
        evaluate(tag, null);
        assertEquals("</select><script>x()</script>", label(out.getContent()));
    }

    /**
     * The value is data: the browser submits exactly the value, and the option
     * that the submitted value matches is selected when the form is shown
     * again.
     */
    public void testValue_IsSubmittedAsGivenAndSelectedWhenMatched() throws Exception {
        String output = runOptionTagSelecting("a&b", "a&b");
        assertEquals("a&b", TestAttributeValueEncoding.attribute(output, "value"));
        assertTrue(output, output.contains("selected=\"selected\""));

        output = runOptionTagSelecting("a&amp;b", "a&amp;b");
        assertEquals("a&amp;b", TestAttributeValueEncoding.attribute(output, "value"));
        assertTrue(output, output.contains("selected=\"selected\""));
    }

    /**
     * Without body text or a key the value is shown as the label. It is
     * still data, so the label shows exactly what the browser submits.
     */
    public void testLabel_WithoutTextOrKey_ShowsTheValueAsGiven() throws Exception {
        String[] values = {"A&B", "A&amp;B", "&copy;", "&#65;", "</option><b>"};
        for (int i = 0; i < values.length; i++) {
            out = new MockJspWriter();
            pageContext.setJspWriter(out);
            String output = runOptionTag(values[i], null);
            assertEquals(values[i], TestAttributeValueEncoding.attribute(output, "value"));
            assertEquals(values[i], label(output));
        }
    }

    /** Body text is HTML even when it is the same string as the value. */
    public void testText_EqualToTheValue_IsHtml() throws Exception {
        String output = runOptionTag("A&amp;B", "A&amp;B");
        assertEquals("A&amp;B", TestAttributeValueEncoding.attribute(output, "value"));
        assertEquals("A&B", label(output));
    }

    /** A label from an overridden text() is HTML, as it was before. */
    public void testText_FromSubclass_IsHtml() throws Exception {
        stubSelectTag();
        OptionTag tag = new OptionTag() {
            protected String text() {
                return "A&amp;B";
            }
        };
        tag.setPageContext(pageContext);
        tag.setValue("v");
        assertEquals("A&B", label(tag.renderOptionElement()));
    }

    /** A subclass that falls back on super.text() gets the value as data. */
    public void testText_FromSubclassFallingBackOnTheValue_IsData() throws Exception {
        stubSelectTag();
        OptionTag tag = new OptionTag() {
            protected String text() throws JspException {
                return super.text();
            }
        };
        tag.setPageContext(pageContext);
        tag.setValue("A&amp;B");
        assertEquals("A&amp;B", label(tag.renderOptionElement()));
    }

    // ------------------------------------------------------------------
    // filter=false — backward compatibility
    // ------------------------------------------------------------------

    /**
     * When filter is explicitly set to false, values and text must NOT be
     * escaped, preserving backward compatibility for users who need raw HTML.
     */
    public void testOption_FilterFalse_NoEscaping() throws Exception {
        stubSelectTag();
        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue("a\"b");
        tag.setFilter(false);
        evaluate(tag, text("<b>bold</b>"));

        String output = out.getContent();
        assertTrue("With filter=false, raw double-quote must appear",
                output.contains("value=\"a\"b\""));
        assertTrue("With filter=false, raw HTML must appear in text",
                output.contains("<b>bold</b>"));
    }
}
