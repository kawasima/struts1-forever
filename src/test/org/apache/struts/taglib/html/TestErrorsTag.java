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

import javax.servlet.jsp.tagext.Tag;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.config.impl.ModuleConfigImpl;
import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockJspWriter;
import org.apache.struts.mock.MockPageContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;
import org.apache.struts.util.MessageResources;

/**
 * Tests for {@link ErrorsTag} output escaping (H-2 / CVE-2012-1007).
 *
 * <p>A message created with <code>resource=false</code> is user data and is
 * HTML-escaped as a whole. A message looked up in the message resources is
 * written as HTML; only the text of its replacement arguments is escaped.
 * The header, footer, prefix and suffix come from the message resources and
 * are written as they are. {@link TestHtmlMessageFormatter} covers the
 * formatting of resource messages in detail.</p>
 */
public class TestErrorsTag extends TestCase {

    private MockServletContext context;
    private MockServletConfig config;
    private MockHttpSession session;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockPageContext pageContext;
    private MockJspWriter out;

    protected void setUp() {
        context = new MockServletContext();
        // ErrorsTag calls TagUtils.present() which needs:
        //   1. ModuleConfig in context under MODULE_KEY + "" (for getModuleConfig())
        //   2. MessageResources in context under MESSAGES_KEY (for retrieveMessageResources())
        // We use a non-existent bundle so all header/prefix/suffix/footer key
        // lookups return null → isPresent() returns false → they are skipped.
        context.setAttribute(Globals.MODULE_KEY + "", new ModuleConfigImpl(""));
        MessageResources emptyResources = MessageResources.getMessageResources(
                "org.apache.struts.taglib.html.EmptyBundle");
        context.setAttribute(Globals.MESSAGES_KEY, emptyResources);
        config = new MockServletConfig(context);
        session = new MockHttpSession(context);
        request = new MockHttpServletRequest(session);
        response = new MockHttpServletResponse();
        pageContext = new MockPageContext(config, request, response);
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /**
     * Stores the given ActionMessages in REQUEST_SCOPE under Globals.ERROR_KEY,
     * runs ErrorsTag.doStartTag(), and returns the captured output.
     * The tag is configured with no header/prefix/suffix/footer (no bundle
     * keys match, so those strings are skipped).
     */
    private String runErrorsTag(ActionMessages messages) throws Exception {
        request.setAttribute(Globals.ERROR_KEY, messages);
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        ErrorsTag tag = new ErrorsTag();
        tag.setPageContext(pageContext);

        tag.doStartTag();
        tag.doEndTag();

        return out.getContent();
    }

    // ------------------------------------------------------------------
    // No errors — tag is silent
    // ------------------------------------------------------------------

    public void testNoMessages_ProducesNoOutput() throws Exception {
        String output = runErrorsTag(new ActionMessages());
        assertEquals("Empty ActionMessages must produce no output", "", output);
    }

    public void testNullMessagesAttribute_ProducesNoOutput() throws Exception {
        out = new MockJspWriter();
        pageContext.setJspWriter(out);

        ErrorsTag tag = new ErrorsTag();
        tag.setPageContext(pageContext);
        tag.doStartTag();
        tag.doEndTag();

        assertEquals("Null errors attribute must produce no output", "", out.getContent());
    }

    // ------------------------------------------------------------------
    // Non-resource message (resource=false) — key rendered literally (H-2)
    // ------------------------------------------------------------------

    /**
     * A non-resource ActionMessage key containing an XSS payload must be
     * HTML-escaped in the output (H-2 / CVE-2012-1007).
     */
    public void testNonResourceMessage_EscapesScriptTag() throws Exception {
        ActionMessages messages = new ActionMessages();
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("<script>alert('XSS')</script>", false));

        String output = runErrorsTag(messages);

        assertFalse("Raw <script> tag must not appear in ErrorsTag output (H-2)",
            output.contains("<script>"));
        assertTrue("Output must contain &lt;script&gt; instead",
            output.contains("&lt;script&gt;"));
    }

    /**
     * A non-resource message containing a double-quote must be HTML-escaped
     * to prevent attribute injection (H-2).
     */
    public void testNonResourceMessage_EscapesDoubleQuote() throws Exception {
        ActionMessages messages = new ActionMessages();
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("\" onmouseover=\"alert(1)", false));

        String output = runErrorsTag(messages);

        assertFalse("Raw double-quote must not appear in ErrorsTag output (H-2)",
            output.contains("\" onmouseover="));
        assertTrue("&quot; must appear in output", output.contains("&quot;"));
    }

    /**
     * A non-resource message containing only plain text must pass through
     * unchanged — escaping must not corrupt safe content.
     */
    public void testNonResourceMessage_PlainText_PassesThrough() throws Exception {
        ActionMessages messages = new ActionMessages();
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("Field is required.", false));

        String output = runErrorsTag(messages);

        assertTrue("Plain text must appear in output unchanged",
                output.contains("Field is required."));
    }

    // ------------------------------------------------------------------
    // Resource message — template is HTML, arguments are escaped
    // ------------------------------------------------------------------

    /**
     * Markup written in the message resources is rendered as HTML; the
     * argument is escaped.
     */
    public void testResourceMessage_KeepsTemplateMarkup_EscapesArgument()
            throws Exception {
        context.setAttribute(Globals.MESSAGES_KEY,
                MessageResources.getMessageResources(
                        "org.apache.struts.taglib.html.TestErrorsTagMessages"));
        ActionMessages messages = new ActionMessages();
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("login.failed", "<script>alert(1)</script>"));

        String output = runErrorsTag(messages);

        assertEquals(
                "Login failed for user <strong>&lt;script&gt;alert(1)&lt;/script&gt;</strong>.",
                output);
    }

    /**
     * The header, footer, prefix and suffix are written as they are around
     * each message; a literal message is escaped whole.
     */
    public void testHeaderFooterPrefixSuffix_WrapEachMessage() throws Exception {
        context.setAttribute(Globals.MESSAGES_KEY,
                MessageResources.getMessageResources(
                        "org.apache.struts.taglib.html.TestErrorsTagLayoutMessages"));
        ActionMessages messages = new ActionMessages();
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("error.example", "<x>"));
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("a <b>", false));

        String output = runErrorsTag(messages);

        assertEquals(
                "<ul><li><strong>&lt;x&gt;</strong></li><li>a &lt;b&gt;</li></ul>",
                output);
    }

    // ------------------------------------------------------------------
    // Return value
    // ------------------------------------------------------------------

    public void testWithMessages_ReturnsEvalBodyInclude() throws Exception {
        ActionMessages messages = new ActionMessages();
        messages.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("some.error.key", false));
        request.setAttribute(Globals.ERROR_KEY, messages);

        ErrorsTag tag = new ErrorsTag();
        tag.setPageContext(pageContext);

        int result = tag.doStartTag();
        assertEquals(Tag.EVAL_BODY_INCLUDE, result);
    }
}
