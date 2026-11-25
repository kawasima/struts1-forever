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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.jsp.PageContext;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockJspWriter;
import org.apache.struts.mock.MockPageContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;

/**
 * The browser reads the attribute values that tags write as intended
 * (issue #4). Values that are HTML, such as URLs Struts builds with
 * <code>&amp;amp;</code> or event handlers, keep their character references;
 * values that are data, such as the value a form submits, are escaped once.
 * A raw quote cannot end an attribute either way.
 *
 * <p>Assertions decode the character references the tags write (see
 * {@link #decode}) and compare what the browser reads, not the markup.</p>
 */
public class TestAttributeValueEncoding extends TestCase {

    private MockPageContext pageContext;
    private MockHttpSession session;
    private MockJspWriter out;

    protected void setUp() {
        MockServletContext context = new MockServletContext();
        session = new MockHttpSession(context);
        pageContext = new MockPageContext(new MockServletConfig(context),
            new MockHttpServletRequest(session), new MockHttpServletResponse());
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
    }

    // ------------------------------------------------------------------
    // URLs that Struts builds with &amp; between parameters
    // ------------------------------------------------------------------

    public void testLinkHref_SeveralParameters_AreAllKept() throws Exception {
        LinkTag tag = linkTag("/member/edit.do", params("id", "1001", "from", "list"));

        tag.doStartTag();

        assertUrl("/member/edit.do", new String[] {"id=1001", "from=list"},
            attribute(out.getContent(), "href"));
    }

    public void testLinkHref_ExistingQueryAndParameters_AreAllKept() throws Exception {
        LinkTag tag = linkTag("/member/list.do?sort=name", params("page", "2", null, null));

        tag.doStartTag();

        assertUrl("/member/list.do", new String[] {"sort=name", "page=2"},
            attribute(out.getContent(), "href"));
    }

    public void testLinkHref_WrittenWithCharacterReference_IsKept() throws Exception {
        LinkTag tag = linkTag("/member/list.do?sort=name&amp;order=asc", null);

        tag.doStartTag();

        assertEquals("/member/list.do?sort=name&order=asc",
            attribute(out.getContent(), "href"));
    }

    public void testLinkHref_ParamIdAndTransactionToken_AreAllKept() throws Exception {
        pageContext.setAttribute("memberId", "1001", PageContext.PAGE_SCOPE);
        session.setAttribute(Globals.TRANSACTION_TOKEN_KEY, "tok123");
        LinkTag tag = linkTag("/member/edit.do", null);
        tag.setParamId("id");
        tag.setParamName("memberId");
        tag.setTransaction(true);

        tag.doStartTag();

        assertUrl("/member/edit.do",
            new String[] {"id=1001", Constants.TOKEN_KEY + "=tok123"},
            attribute(out.getContent(), "href"));
    }

    public void testFrameSrc_SeveralParameters_AreAllKept() throws Exception {
        pageContext.setAttribute("params", params("id", "1001", "from", "list"),
            PageContext.PAGE_SCOPE);
        FrameTag tag = new FrameTag();
        tag.setPageContext(pageContext);
        tag.setHref("/member/edit.do");
        tag.setName("params");

        tag.doStartTag();

        assertUrl("/member/edit.do", new String[] {"id=1001", "from=list"},
            attribute(out.getContent(), "src"));
    }

    /** ImgTag builds its query string itself, also with &amp;. */
    public void testImgSrc_SeveralParameters_AreAllKept() throws Exception {
        pageContext.setAttribute("params", params("w", "10", "h", "20"),
            PageContext.PAGE_SCOPE);
        ImgTag tag = new ImgTag();
        tag.setPageContext(pageContext);
        tag.setSrc("/img/photo.gif");
        tag.setName("params");

        tag.doStartTag();
        tag.doEndTag();

        assertUrl("/img/photo.gif", new String[] {"w=10", "h=20"},
            attribute(out.getContent(), "src"));
    }

    // ------------------------------------------------------------------
    // Values that a form submits and compares with a form property
    // ------------------------------------------------------------------

    /** The browser submits exactly the value, whatever it contains. */
    public void testRadioValue_IsSubmittedAsGiven() throws Exception {
        RadioTag tag = new RadioTag();
        tag.setPageContext(pageContext);
        tag.setProperty("choice");

        assertEquals("A&B", attribute(tag.renderRadioElement("A&B", ""), "value"));
        assertEquals("A&amp;B", attribute(tag.renderRadioElement("A&amp;B", ""), "value"));
    }

    public void testMultiboxValue_IsSubmittedAsGiven() throws Exception {
        assertEquals("A&B", attribute(multiboxValue("A&B"), "value"));
        assertEquals("A&amp;B", attribute(multiboxValue("A&amp;B"), "value"));
    }

    /** The submitted value is the one isChecked() compares with the property. */
    public void testCheckboxValue_IsSubmittedAsGivenAndChecked() throws Exception {
        assertCheckboxSubmitsAndChecks("A&B");
        assertCheckboxSubmitsAndChecks("A&amp;B");
    }

    private void assertCheckboxSubmitsAndChecks(String value) throws Exception {
        Map form = new HashMap();
        form.put("choice", value);
        pageContext.setAttribute("form", form, PageContext.PAGE_SCOPE);
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
        CheckboxTag tag = new CheckboxTag();
        tag.setPageContext(pageContext);
        tag.setName("form");
        tag.setProperty("choice");
        tag.setValue(value);

        tag.doStartTag();

        String output = out.getContent();
        assertEquals(value, attribute(output, "value"));
        assertTrue(output, output.indexOf("checked=\"checked\"") >= 0);
    }

    /**
     * The name is the request parameter that the form property is populated
     * from, so it is data as well.
     */
    public void testName_IsSubmittedAsGiven() throws Exception {
        RadioTag radio = new RadioTag();
        radio.setPageContext(pageContext);
        radio.setProperty("map(A&amp;B)");
        assertEquals("map(A&amp;B)",
            attribute(radio.renderRadioElement("v", ""), "name"));

        SubmitTag submit = new SubmitTag();
        submit.setPageContext(pageContext);
        submit.setProperty("map(A&amp;B)");
        submit.doEndTag();
        assertEquals("map(A&amp;B)", attribute(out.getContent(), "name"));
    }

    // ------------------------------------------------------------------
    // Values that page authors write as HTML
    // ------------------------------------------------------------------

    /**
     * The label of a submit button is HTML, as in 1.2.9-forever-2, so that
     * existing pages display as before. The browser submits the decoded
     * label, so a LookupDispatchAction key whose message contains a
     * character reference does not match it, as in 1.2.9.
     */
    public void testSubmitValue_IsHtml() throws Exception {
        SubmitTag tag = new SubmitTag();
        tag.setPageContext(pageContext);
        tag.setValue("Save &amp; Close");

        tag.doEndTag();

        assertEquals("Save & Close", attribute(out.getContent(), "value"));
    }

    /**
     * Data in an event handler is escaped for a JavaScript string, and the
     * code built from it is escaped with filter(); the browser then runs the
     * code as built, even if the data contains a character reference.
     */
    public void testOnclick_CodeEscapedWithFilter_RunsAsBuilt() throws Exception {
        String code = "show('&#39;);alert(1);//')";
        StringBuffer results = new StringBuffer();
        new TextTag().prepareAttribute(results, "onclick",
            org.apache.struts.taglib.TagUtils.getInstance().filter(code));

        assertEquals(code, attribute(results.toString(), "onclick"));
    }

    public void testOnclick_WithCharacterReferences_KeepsItsMeaning() throws Exception {
        StringBuffer results = new StringBuffer();
        new TextTag().prepareAttribute(results, "onclick",
            "location.href='edit.do?id=1&amp;from=list'");

        assertEquals("location.href='edit.do?id=1&from=list'",
            attribute(results.toString(), "onclick"));
    }

    public void testTitle_WithCharacterReference_IsKept() throws Exception {
        StringBuffer results = new StringBuffer();
        new TextTag().prepareAttribute(results, "title", "&copy; 2026");

        assertEquals(" title=\"&copy; 2026\"", results.toString());
    }

    /** Escaping keeps the value inside its attribute. */
    public void testAttributeValue_WithRawQuote_CannotEndTheAttribute() throws Exception {
        StringBuffer results = new StringBuffer();
        new TextTag().prepareAttribute(results, "title", "x\" onmouseover=\"alert(1)");

        assertEquals(-1, results.toString().indexOf("onmouseover=\""));
        assertEquals("x\" onmouseover=\"alert(1)", attribute(results.toString(), "title"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private LinkTag linkTag(String href, Map params) {
        if (params != null) {
            pageContext.setAttribute("params", params, PageContext.PAGE_SCOPE);
        }
        LinkTag tag = new LinkTag();
        tag.setPageContext(pageContext);
        tag.setHref(href);
        if (params != null) {
            tag.setName("params");
        }
        return tag;
    }

    private static Map params(String name1, String value1, String name2, String value2) {
        Map params = new HashMap();
        params.put(name1, value1);
        if (name2 != null) {
            params.put(name2, value2);
        }
        return params;
    }

    private String multiboxValue(String value) throws Exception {
        MultiboxTag tag = new MultiboxTag();
        tag.setPageContext(pageContext);
        tag.setValue(value);
        StringBuffer results = new StringBuffer();
        tag.prepareValue(results);
        return results.toString();
    }

    /**
     * Checks the path and the query parameters of a decoded URL, in any
     * order but counting repeated parameters.
     */
    private static void assertUrl(String path, String[] parameters, String url) {
        int question = url.indexOf('?');
        assertTrue(url, question > 0);
        assertEquals(url, path, url.substring(0, question));
        String[] actual = url.substring(question + 1).split("&");
        String[] expected = (String[]) parameters.clone();
        Arrays.sort(actual);
        Arrays.sort(expected);
        assertEquals(url, Arrays.asList(expected), Arrays.asList(actual));
    }

    /**
     * The value of the first double-quoted attribute <code>name</code> in
     * <code>html</code>, decoded with {@link #decode}.
     */
    static String attribute(String html, String name) {
        String start = " " + name + "=\"";
        int from = html.indexOf(start);
        assertTrue(name + " in " + html, from >= 0);
        from += start.length();
        int end = html.indexOf('"', from);
        assertTrue(html, end >= 0);
        return decode(html.substring(from, end));
    }

    /**
     * Decodes the character references that <code>TagUtils.filter()</code>
     * and <code>LegacyMarkup.escape()</code> write: <code>&amp;amp;</code>,
     * <code>&amp;quot;</code>, <code>&amp;lt;</code>, <code>&amp;gt;</code>
     * and decimal references below U+10000. Any other reference is left as
     * it is. This is not a general-purpose HTML decoder.
     */
    static String decode(String value) {
        StringBuffer decoded = new StringBuffer();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            int semicolon = value.indexOf(';', i);
            if (c == '&' && semicolon > i) {
                String reference = value.substring(i + 1, semicolon);
                String character = character(reference);
                if (character != null) {
                    decoded.append(character);
                    i = semicolon + 1;
                    continue;
                }
            }
            decoded.append(c);
            i++;
        }
        return decoded.toString();
    }

    private static String character(String reference) {
        if (reference.equals("amp")) {
            return "&";
        } else if (reference.equals("quot")) {
            return "\"";
        } else if (reference.equals("lt")) {
            return "<";
        } else if (reference.equals("gt")) {
            return ">";
        } else if (reference.matches("#[0-9]{1,5}")) {
            int code = Integer.parseInt(reference.substring(1));
            return code < 0x10000 ? String.valueOf((char) code) : null;
        }
        return null;
    }
}
