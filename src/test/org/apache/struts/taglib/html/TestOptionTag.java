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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Locale;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.util.MessageResources;

import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockJspWriter;
import org.apache.struts.mock.MockPageContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;

/**
 * Tests for {@link OptionTag} HTML escaping (H-3 fix).
 *
 * <p>With {@code filter} {@code true} (the default) the value, which is data,
 * is escaped with {@link org.apache.struts.taglib.TagUtils#filter(String)} as
 * in {@code <html:options>}; the label, which is HTML, keeps its character
 * references and only {@code " ' < >} are escaped (issue #4).</p>
 */
public class TestOptionTag extends TestCase {

    /**
     * Test subclass that allows injecting body text directly, bypassing the
     * {@code doAfterBody()} body-content lifecycle. This is necessary because
     * {@code OptionTag.doStartTag()} resets {@code text = null} and the real
     * body-content mechanism requires a full JSP container.
     *
     * <p>{@code doStartTag()} is overridden to populate {@code this.text}
     * from the injected value, mirroring what {@code doAfterBody()} does in a
     * real container after the body is evaluated.</p>
     */
    private static class InjectableOptionTag extends OptionTag {
        private String injectedText = null;

        public void setBodyText(String t) {
            this.injectedText = t;
        }

        public int doStartTag() throws JspException {
            this.text = injectedText;
            return EVAL_BODY_TAG;
        }
    }

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
    // Helper: register a minimal SelectTag stub in page context so
    // OptionTag.selectTag() does not throw, then run OptionTag.
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

    /**
     * Runs OptionTag with the given value and optional body text,
     * returns captured output.
     *
     * <p>When {@code text} is non-null, an {@link InjectableOptionTag} is
     * used so that body text can be injected without a real JSP container.
     * When {@code text} is null, a plain {@link OptionTag} is used to
     * exercise the value-as-label fallback path.</p>
     */
    private String runOptionTag(String value, String text) throws Exception {
        stubSelectTag();
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        if (text != null) {
            InjectableOptionTag tag = new InjectableOptionTag();
            tag.setPageContext(pageContext);
            tag.setValue(value);
            tag.setBodyText(text);
            tag.doStartTag();
            tag.doEndTag();
        } else {
            OptionTag tag = new OptionTag();
            tag.setPageContext(pageContext);
            tag.setValue(value);
            tag.doStartTag();
            tag.doEndTag();
        }

        return out.getContent();
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

    // ------------------------------------------------------------------
    // value attribute — must be HTML-escaped (H-3)
    // ------------------------------------------------------------------

    /**
     * A double-quote in the value attribute must be escaped as {@code &quot;}
     * to prevent breaking out of the attribute context (H-3).
     */
    public void testValueAttribute_EscapesDoubleQuote() throws Exception {
        String output = runOptionTag("a\"b", "Label");
        assertFalse("Raw double-quote in value attribute enables attribute injection (H-3)",
            output.contains("value=\"a\"b\""));
        assertTrue("value must contain &quot; instead of raw \"",
            output.contains("&quot;"));
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
        assertFalse("Raw <script> in option text must be escaped (H-3)",
            output.contains("<script>alert(1)</script>"));
        assertTrue("&lt;script&gt; must appear in option text",
            output.contains("&lt;script&gt;"));
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
    // Character references — HTML labels and data values (issue #4)
    // ------------------------------------------------------------------

    /** &nbsp; and &eacute; display as they did in 1.2.9-forever-2. */
    public void testText_WithCharacterReferences_IsKept() throws Exception {
        String output = runOptionTag("child", "&nbsp;&nbsp;Caf&eacute;");
        assertTrue(output, output.contains(">&nbsp;&nbsp;Caf&eacute;</option>"));
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
        String[] values = {"A&B", "A&amp;B", "&copy;", "&#65;"};
        for (int i = 0; i < values.length; i++) {
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
    // Labels that are already HTML (issue #5)
    // ------------------------------------------------------------------

    /**
     * Message resources hold HTML because {@code <bean:message>} writes them
     * as given, so a label looked up by key keeps its character references.
     */
    public void testLabel_FromKey_KeepsCharacterReferences() throws Exception {
        pageContext.setAttribute(Globals.MESSAGES_KEY,
                new FixedMessageResources("&lt;Select One&gt;&nbsp;"),
                PageContext.REQUEST_SCOPE);
        stubSelectTag();

        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue("");
        tag.setKey("typeForm.option.select.one");
        tag.doStartTag();
        tag.doEndTag();

        assertTrue(out.getContent(),
                out.getContent().contains(">&lt;Select One&gt;&nbsp;</option>"));
    }

    /** A message looked up by key still cannot add elements to the page. */
    public void testLabel_FromKey_EscapesMarkup() throws Exception {
        pageContext.setAttribute(Globals.MESSAGES_KEY,
                new FixedMessageResources("</select><script>x()</script>"),
                PageContext.REQUEST_SCOPE);
        stubSelectTag();

        OptionTag tag = new OptionTag();
        tag.setPageContext(pageContext);
        tag.setValue("v");
        tag.setKey("k");

        assertEquals("</select><script>x()</script>",
                label(tag.renderOptionElement()));
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
        tag.doStartTag();

        StringBodyContent body = new StringBodyContent(out);
        tag.setBodyContent(body);
        pageContext.setJspWriter(body);
        org.apache.struts.taglib.bean.WriteTag write =
                new org.apache.struts.taglib.bean.WriteTag();
        write.setPageContext(pageContext);
        write.setName("member");
        write.doStartTag();
        write.doEndTag();
        pageContext.setJspWriter(out);
        tag.doAfterBody();
        tag.doEndTag();

        String output = out.getContent();
        assertTrue(output,
                output.contains(">Tom &amp; Jerry &lt;TJ&gt;</option>"));
        assertEquals("Tom & Jerry <TJ>", label(output));
    }

    /** Body text written as a character reference, as in {@code &nbsp;}. */
    public void testText_BlankOptionWithNbsp_IsKept() throws Exception {
        String output = runOptionTag("", "&nbsp;");
        assertTrue(output, output.contains(">&nbsp;</option>"));
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

    /** Body content that keeps what is written to it, as a container does. */
    private static class StringBodyContent extends BodyContent {
        private final StringBuffer buffer = new StringBuffer();

        StringBodyContent(JspWriter enclosing) {
            super(enclosing);
        }

        public String getString() {
            return buffer.toString();
        }

        public Reader getReader() {
            return new StringReader(getString());
        }

        public void writeOut(Writer w) throws IOException {
            w.write(getString());
        }

        public void write(char[] cbuf, int off, int len) {
            buffer.append(cbuf, off, len);
        }

        public void newLine() { buffer.append('\n'); }
        public void print(boolean b) { buffer.append(b); }
        public void print(char c) { buffer.append(c); }
        public void print(int i) { buffer.append(i); }
        public void print(long l) { buffer.append(l); }
        public void print(float f) { buffer.append(f); }
        public void print(double d) { buffer.append(d); }
        public void print(char[] s) { buffer.append(s); }
        public void print(String s) { buffer.append(s); }
        public void print(Object o) { buffer.append(o); }
        public void println() { newLine(); }
        public void println(boolean b) { print(b); newLine(); }
        public void println(char c) { print(c); newLine(); }
        public void println(int i) { print(i); newLine(); }
        public void println(long l) { print(l); newLine(); }
        public void println(float f) { print(f); newLine(); }
        public void println(double d) { print(d); newLine(); }
        public void println(char[] s) { print(s); newLine(); }
        public void println(String s) { print(s); newLine(); }
        public void println(Object o) { print(o); newLine(); }
        public void clear() { buffer.setLength(0); }
        public void clearBuffer() { buffer.setLength(0); }
        public void flush() { }
        public void close() { }
        public int getRemaining() { return 0; }
    }

    /** The label of the option element, decoded as the browser reads it. */
    private static String label(String html) {
        int start = html.indexOf('>') + 1;
        int end = html.indexOf("</option>");
        assertTrue(html, start > 0 && end >= start);
        return TestAttributeValueEncoding.decode(html.substring(start, end));
    }

    private String runOptionTagSelecting(String value, String selected) throws Exception {
        stubSelectTag().match = new String[] {selected};
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        InjectableOptionTag tag = new InjectableOptionTag();
        tag.setPageContext(pageContext);
        tag.setValue(value);
        tag.setBodyText("Label");
        tag.doStartTag();
        tag.doEndTag();

        return out.getContent();
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
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        InjectableOptionTag tag = new InjectableOptionTag();
        tag.setPageContext(pageContext);
        tag.setValue("a\"b");
        tag.setBodyText("<b>bold</b>");
        tag.setFilter(false);
        tag.doStartTag();
        tag.doEndTag();

        String output = out.getContent();
        assertTrue("With filter=false, raw double-quote must appear",
                output.contains("value=\"a\"b\""));
        assertTrue("With filter=false, raw HTML must appear in text",
                output.contains("<b>bold</b>"));
    }
}
