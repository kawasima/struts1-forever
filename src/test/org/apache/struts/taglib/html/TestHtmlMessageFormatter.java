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

import java.util.Date;
import java.util.Locale;

import junit.framework.TestCase;

import org.apache.struts.util.MessageResources;
import org.apache.struts.util.PropertyMessageResources;
import org.apache.struts.util.PropertyMessageResourcesFactory;

/**
 * Tests for {@link HtmlMessageFormatter}: the template from the message
 * resources is written as HTML, and the text of each argument is escaped.
 */
public class TestHtmlMessageFormatter extends TestCase {

    private static final String BUNDLE =
            "org.apache.struts.taglib.html.TestErrorsTagMessages";

    private static final MessageResources RESOURCES =
            MessageResources.getMessageResources(BUNDLE);

    private static String format(String key, Object[] args) {
        return HtmlMessageFormatter.format(RESOURCES, Locale.ENGLISH, key, args);
    }

    public void testTemplateMarkup_IsKept_ArgumentMarkup_IsEscaped() {
        assertEquals(
                "Login failed for user <strong>&lt;strong&gt;alice&lt;/strong&gt;</strong>.",
                format("login.failed", new Object[] {"<strong>alice</strong>"}));
    }

    public void testArgumentQuotesAndAmpersand_AreEscaped() {
        assertEquals(
                "Login failed for user <strong>&quot;a&amp;b&#39;</strong>.",
                format("login.failed", new Object[] {"\"a&b'"}));
    }

    public void testArgumentUsedTwice_IsEscapedEachTime() {
        assertEquals("<b>&lt;x&gt;</b> and &lt;x&gt;",
                format("repeat", new Object[] {"<x>"}));
    }

    /**
     * Escaping applies to the formatted text, so a non-String argument whose
     * toString() returns markup is escaped too, and it is formatted once.
     */
    public void testObjectArgument_IsFormattedOnceAndEscaped() {
        final int[] calls = new int[1];
        Object value = new Object() {
            public String toString() {
                calls[0]++;
                return "<x>";
            }
        };

        assertEquals("<b>&lt;x&gt;</b> and &lt;x&gt;",
                format("repeat", new Object[] {value}));
        assertEquals("toString() calls", 2, calls[0]);
    }

    /**
     * Number and date subformats get the locale as getMessage() gives it
     * to them; Locale.GERMANY makes the grouping and month name visible.
     */
    public void testNumberAndDateArguments_AreFormattedAsGetMessageFormatsThem() {
        Object[] args = new Object[] {new Double(1234.5), new Date(0L)};

        assertEquals(RESOURCES.getMessage(Locale.GERMANY, "formats", args),
                HtmlMessageFormatter.format(
                        RESOURCES, Locale.GERMANY, "formats", args));
    }

    public void testSingleQuote_IsHandledAsGetMessageHandlesIt() {
        Object[] args = new Object[] {"x"};
        MessageResources noEscape = MessageResources.getMessageResources(BUNDLE);
        noEscape.setEscape(false);

        assertEquals("It's <em>x</em>", format("quote", args));
        assertEquals(noEscape.getMessage(Locale.ENGLISH, "quote", args),
                HtmlMessageFormatter.format(
                        noEscape, Locale.ENGLISH, "quote", args));
    }

    /** Without arguments the template is written without MessageFormat. */
    public void testNullArguments_TemplateIsWrittenAsIs() {
        assertEquals("It's <em>{0}</em>", format("quote", null));
    }

    public void testEmptyArguments_PlaceholderIsKept() {
        assertEquals("<b>{0}</b> and {0}", format("repeat", new Object[0]));
    }

    /**
     * MessageFormat marks the whole text chosen by a ChoiceFormat as the
     * argument, so markup inside a choice is escaped.
     */
    public void testChoiceFormatMarkup_IsEscaped() {
        MessageResources noEscape = MessageResources.getMessageResources(BUNDLE);
        noEscape.setEscape(false);

        assertEquals("&lt;i&gt;one&lt;/i&gt;",
                HtmlMessageFormatter.format(noEscape, Locale.ENGLISH,
                        "choice", new Object[] {new Integer(1)}));
    }

    /** U+FFFF is CharacterIterator.DONE; it must not end the message. */
    public void testDoneCharacterInTemplate_DoesNotEndMessage() {
        assertEquals("a￿b <b>&lt;x&gt;</b> c",
                format("uffff", new Object[] {"<x>"}));
    }

    /**
     * With returnNull=false a missing key gives "???key???", which holds
     * the key, not a template.
     */
    public void testMissingKey_ReturnNullFalse_IsEscaped() {
        MessageResources noNull = MessageResources.getMessageResources(BUNDLE);
        noNull.setReturnNull(false);

        assertEquals("???en.&lt;x&gt;???", HtmlMessageFormatter.format(
                noNull, Locale.ENGLISH, "<x>", null));
        assertEquals("???en.&lt;x&gt;???", HtmlMessageFormatter.format(
                noNull, Locale.ENGLISH, "<x>", new Object[] {"a"}));
    }

    /**
     * isPresent() takes a message that starts and ends with "???" for a
     * missing key, so such a template is escaped whole.
     */
    public void testTemplateLookingLikeMissingKey_IsEscapedWhole() {
        assertEquals("???&lt;strong&gt;x&lt;/strong&gt;???",
                format("ambiguous", new Object[] {"x"}));
    }

    /** returnNull is true by default in MessageResourcesFactory. */
    public void testMissingKey_ReturnNullTrue_ReturnsNull() {
        assertNull(format("missing", null));
        assertNull(format("missing", new Object[] {"a"}));
    }

    /**
     * A subclass may format messages its own way, so its message is
     * escaped whole.
     */
    public void testSubclassOfPropertyMessageResources_MessageIsEscapedWhole() {
        MessageResources custom = new PropertyMessageResources(
                new PropertyMessageResourcesFactory(), BUNDLE) {
            public String getMessage(Locale locale, String key, Object[] args) {
                String template = getMessage(locale, key);
                int at = template.indexOf("%s");
                return template.substring(0, at) + args[0]
                        + template.substring(at + 2);
            }
        };

        assertEquals("&lt;b&gt;x&lt;/b&gt;",
                HtmlMessageFormatter.format(
                        custom, Locale.ENGLISH, "custom", new Object[] {"x"}));
    }
}
