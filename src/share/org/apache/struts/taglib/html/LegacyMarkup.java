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

/**
 * <p>Escapes HTML that a page author, a message resource or Struts itself
 * wrote, so that it cannot close the attribute or open an element it is
 * written into, while character references in it keep their meaning.</p>
 *
 * <p>Attribute values given to {@link BaseHandlerTag#prepareAttribute} and
 * the label of <code>&lt;html:option&gt;</code> have always been written out
 * as given: they are HTML, not data. A character reference in
 * them, such as <code>&amp;nbsp;</code>, is meant to be read by the browser,
 * and Struts itself separates URL parameters with <code>&amp;amp;</code>.
 * Escaping <code>&amp;</code> again would change what the browser reads.
 * This escapes only <code>"</code>, <code>'</code>, <code>&lt;</code> and
 * <code>&gt;</code>; the browser decodes each of them back to the same
 * character, so the value it reads does not change.</p>
 *
 * <p>This is not an encoder for data: a bean property or a submitted value
 * is escaped with <code>TagUtils.filter()</code>, which escapes
 * <code>&amp;</code> as well. A raw <code>&amp;</code> is left as it is, so
 * the result is not well-formed XHTML unless the input was. Neither escaping
 * makes a value safe inside a script, a URL or a style.</p>
 */
final class LegacyMarkup {

    private LegacyMarkup() {
    }

    /**
     * @param html HTML text, possibly with character references
     * @return the text with <code>"</code>, <code>'</code>, <code>&lt;</code>
     *  and <code>&gt;</code> replaced by character references
     */
    static String escape(String html) {
        if (html == null) {
            return null;
        }
        StringBuffer escaped = null;
        for (int i = 0; i < html.length(); i++) {
            String reference;
            switch (html.charAt(i)) {
                case '"':
                    reference = "&quot;";
                    break;
                case '\'':
                    reference = "&#39;";
                    break;
                case '<':
                    reference = "&lt;";
                    break;
                case '>':
                    reference = "&gt;";
                    break;
                default:
                    reference = null;
            }
            if (reference != null) {
                if (escaped == null) {
                    escaped = new StringBuffer(html.length() + 16);
                    escaped.append(html.substring(0, i));
                }
                escaped.append(reference);
            } else if (escaped != null) {
                escaped.append(html.charAt(i));
            }
        }
        return escaped == null ? html : escaped.toString();
    }
}
