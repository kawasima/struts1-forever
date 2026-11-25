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

import java.util.HashMap;
import java.util.Map;

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
 * Attribute values that tags pass to {@code prepareAttribute()} are HTML and
 * reach the browser with the value they had in 1.2.9-forever-2: a value
 * already containing character references is not escaped a second time
 * (issue #4). A raw quote still cannot end the attribute.
 */
public class TestAttributeValueEncoding extends TestCase {

    private MockPageContext pageContext;
    private MockJspWriter out;

    protected void setUp() {
        MockServletContext context = new MockServletContext();
        MockHttpServletRequest request =
            new MockHttpServletRequest(new MockHttpSession(context));
        pageContext = new MockPageContext(new MockServletConfig(context), request,
            new MockHttpServletResponse());
        out = new MockJspWriter();
        pageContext.setJspWriter(out);
    }

    // ------------------------------------------------------------------
    // URLs that Struts builds with &amp; between parameters
    // ------------------------------------------------------------------

    public void testLinkHref_SeveralParameters_AreSeparatedOnce() throws Exception {
        Map params = new HashMap();
        params.put("id", "1001");
        params.put("from", "list");
        pageContext.setAttribute("params", params, PageContext.PAGE_SCOPE);
        LinkTag tag = new LinkTag();
        tag.setPageContext(pageContext);
        tag.setHref("/member/edit.do");
        tag.setName("params");

        tag.doStartTag();

        String output = out.getContent();
        assertTrue(output, output.indexOf("&amp;") >= 0);
        assertEquals(output, -1, output.indexOf("&amp;amp;"));
        assertTrue(output, output.indexOf("id=1001") >= 0);
        assertTrue(output, output.indexOf("from=list") >= 0);
    }

    public void testLinkHref_WrittenWithCharacterReference_IsKept() throws Exception {
        LinkTag tag = new LinkTag();
        tag.setPageContext(pageContext);
        tag.setHref("/member/list.do?sort=name&amp;order=asc");

        tag.doStartTag();

        assertTrue(out.getContent(),
            out.getContent().indexOf("href=\"/member/list.do?sort=name&amp;order=asc\"") >= 0);
    }

    /** ImgTag builds its query string itself, also with &amp;. */
    public void testImgSrc_SeveralParameters_AreSeparatedOnce() throws Exception {
        Map params = new HashMap();
        params.put("w", "10");
        params.put("h", "20");
        pageContext.setAttribute("params", params, PageContext.PAGE_SCOPE);
        ImgTag tag = new ImgTag();
        tag.setPageContext(pageContext);
        tag.setSrc("/img/photo.gif");
        tag.setName("params");

        tag.doStartTag();
        tag.doEndTag();

        String output = out.getContent();
        assertTrue(output, output.indexOf("&amp;") >= 0);
        assertEquals(output, -1, output.indexOf("&amp;amp;"));
    }

    // ------------------------------------------------------------------
    // Values that tags escape with TagUtils.filter() before passing them
    // ------------------------------------------------------------------

    /** The browser submits A&B, the value of the bean property. */
    public void testRadioValue_WithAmpersand_IsEscapedOnce() throws Exception {
        RadioTag tag = new RadioTag();
        tag.setPageContext(pageContext);
        tag.setProperty("choice");

        String output = tag.renderRadioElement("A&B", "");

        assertTrue(output, output.indexOf("value=\"A&amp;B\"") >= 0);
    }

    public void testMultiboxValue_WithAmpersand_IsEscapedOnce() throws Exception {
        MultiboxTag tag = new MultiboxTag();
        tag.setPageContext(pageContext);
        tag.setValue("A&B");
        StringBuffer results = new StringBuffer();

        tag.prepareValue(results);

        assertEquals(" value=\"A&amp;B\"", results.toString());
    }

    // ------------------------------------------------------------------
    // Values that page authors write as HTML
    // ------------------------------------------------------------------

    public void testOnclick_WithCharacterReferences_KeepsItsMeaning() throws Exception {
        StringBuffer results = new StringBuffer();
        new TextTag().prepareAttribute(results, "onclick",
            "location.href='edit.do?id=1&amp;from=list'");

        assertEquals(" onclick=\"location.href=&#39;edit.do?id=1&amp;from=list&#39;\"",
            results.toString());
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

        assertEquals(" title=\"x&quot; onmouseover=&quot;alert(1)\"", results.toString());
    }
}
