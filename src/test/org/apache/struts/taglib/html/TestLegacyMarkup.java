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

import junit.framework.TestCase;

/**
 * {@link LegacyMarkup#escape} keeps character references, so the browser
 * reads the same value, and escapes the characters that could end an
 * attribute or start an element.
 */
public class TestLegacyMarkup extends TestCase {

    public void testAmpersand_IsKept() {
        assertEquals("A&B", LegacyMarkup.escape("A&B"));
    }

    public void testCharacterReferences_AreKept() {
        assertEquals("A&amp;B &copy; &#65; &nbsp;",
            LegacyMarkup.escape("A&amp;B &copy; &#65; &nbsp;"));
    }

    public void testQuotesAndAngleBrackets_AreEscaped() {
        assertEquals("&quot;&#39;&lt;&gt;", LegacyMarkup.escape("\"'<>"));
    }

    public void testText_WithoutSpecialCharacters_IsUnchanged() {
        assertEquals("plain text", LegacyMarkup.escape("plain text"));
    }

    public void testEmptyAndNull_AreUnchanged() {
        assertEquals("", LegacyMarkup.escape(""));
        assertNull(LegacyMarkup.escape(null));
    }
}
