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

import java.text.AttributedCharacterIterator;
import java.text.MessageFormat;
import java.util.Locale;

import org.apache.struts.util.MessageResources;
import org.apache.struts.util.PropertyMessageResources;
import org.apache.struts.util.ResponseUtils;

/**
 * <p>Formats a message from the message resources as HTML for
 * {@link ErrorsTag}. The template is written as it is; the text that
 * each replacement argument produces is escaped with
 * <code>ResponseUtils.filter()</code>. The escaping is for HTML text only,
 * not for a URL, a script or a style.</p>
 *
 * <p>Only an instance of <code>PropertyMessageResources</code> itself is
 * formatted this way, the way
 * <code>MessageResources.getMessage(Locale, String, Object[])</code>
 * formats it. For any other <code>MessageResources</code>, a subclass
 * included, and for a key that <code>isPresent()</code> does not find, the
 * whole message is escaped.</p>
 */
final class HtmlMessageFormatter {

    private HtmlMessageFormatter() {
    }

    /**
     * Returns the message for <code>key</code> as HTML, or <code>null</code>
     * if the resources return <code>null</code> for it.
     *
     * @param resources the message resources to read the template from
     * @param locale the requested locale, or <code>null</code>
     * @param key the message key
     * @param args the replacement arguments, or <code>null</code> to write
     *  the template without formatting it, as
     *  {@link org.apache.struts.taglib.TagUtils#message} does
     */
    static String format(MessageResources resources, Locale locale,
            String key, Object[] args) {

        // The text returned for a missing key is not a template
        if (resources.getClass() != PropertyMessageResources.class
                || !resources.isPresent(locale, key)) {
            String message = (args == null)
                    ? resources.getMessage(locale, key)
                    : resources.getMessage(locale, key, args);
            return ResponseUtils.filter(message);
        }

        String template = resources.getMessage(locale, key);
        if (args == null) {
            return template;
        }

        // Construct the format as MessageResources.getMessage() does
        MessageFormat format =
                new MessageFormat(escapeQuotes(resources, template));
        format.setLocale((locale == null) ? Locale.getDefault() : locale);

        return escapeArguments(format.formatToCharacterIterator(args));
    }

    /**
     * Returns the text of the iterator, escaping each run that carries a
     * <code>MessageFormat.Field.ARGUMENT</code> attribute.
     */
    private static String escapeArguments(AttributedCharacterIterator it) {
        StringBuffer result = new StringBuffer();
        StringBuffer run = new StringBuffer();

        // DONE is a valid character in the message, so compare indexes
        it.first();
        while (it.getIndex() < it.getEndIndex()) {
            int limit = it.getRunLimit(MessageFormat.Field.ARGUMENT);
            boolean argument =
                    it.getAttribute(MessageFormat.Field.ARGUMENT) != null;

            run.setLength(0);
            while (it.getIndex() < limit) {
                run.append(it.current());
                it.next();
            }

            if (argument) {
                result.append(ResponseUtils.filter(run.toString()));
            } else {
                result.append(run);
            }
        }
        return result.toString();
    }

    /**
     * Doubles single quotes as <code>MessageResources.escape()</code> does.
     */
    private static String escapeQuotes(MessageResources resources,
            String template) {
        if (!resources.isEscape() || template.indexOf('\'') < 0) {
            return template;
        }
        StringBuffer sb = new StringBuffer(template.length() + 8);
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '\'') {
                sb.append('\'');
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
