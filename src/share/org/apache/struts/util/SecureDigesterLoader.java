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
package org.apache.struts.util;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.apache.commons.digester.xmlrules.CircularIncludeException;
import org.apache.commons.digester.xmlrules.DigesterRuleParser;
import org.apache.commons.digester.xmlrules.FromXmlRuleSet;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <p>Creates a <code>Digester</code> from an XML rules file, like
 * <code>org.apache.commons.digester.xmlrules.DigesterLoader</code>, with
 * every XML document involved parsed by a {@link SecureDigester}: the rules
 * file, each rules file it includes with
 * <code>&lt;include path="..."/&gt;</code> at any depth, and the documents
 * that the returned <code>Digester</code> parses.</p>
 *
 * <p>Commons Digester parses an included rules file with a plain
 * <code>Digester</code> that it creates itself. This class replaces the
 * include rule with one that behaves the same way (class path lookup,
 * pattern prefix, circular include detection) but parses with a
 * <code>SecureDigester</code>. If Commons Digester does not register the
 * include rule this class expects, creating the <code>Digester</code> fails
 * rather than leaving includes unrestricted.</p>
 *
 * @since Struts 1.2.9-forever-4
 */
public final class SecureDigesterLoader {

    private static final String INCLUDE_RULE_CLASS =
        DigesterRuleParser.class.getName() + "$IncludeRule";

    private SecureDigesterLoader() {
    }

    /**
     * @param rulesUrl the XML rules file
     * @return a Digester configured with the rules
     * @throws org.apache.commons.digester.xmlrules.XmlLoadException if the
     *  rules cannot be loaded, as <code>DigesterLoader</code> does
     */
    public static Digester createDigester(URL rulesUrl) {
        Digester digester = new SecureDigester();
        digester.addRuleSet(new FromXmlRuleSet(
            rulesUrl, new IncludeSafeRuleParser(), new RulesDigester()));
        return digester;
    }

    /**
     * Parses XML rules files and swaps the include rule that a
     * {@link IncludeSafeRuleParser} registers.
     */
    private static final class RulesDigester extends SecureDigester {

        private IncludeSafeRuleParser registering;
        private boolean includeRuleReplaced;

        public void addRule(String pattern, Rule rule) {
            if (registering != null
                && INCLUDE_RULE_CLASS.equals(rule.getClass().getName())) {
                rule.setDigester(this);
                rule = registering.new SecureIncludeRule(rule);
                includeRuleReplaced = true;
            }
            super.addRule(pattern, rule);
        }
    }

    private static final class IncludeSafeRuleParser extends DigesterRuleParser {

        /** URLs of the rules files being included, to detect a cycle. */
        private final Set includedFiles;

        IncludeSafeRuleParser() {
            this.includedFiles = new HashSet();
        }

        private IncludeSafeRuleParser(Digester targetDigester,
                PatternStack patternStack, Set includedFiles) {
            super(targetDigester);
            this.patternStack = patternStack;
            this.includedFiles = includedFiles;
        }

        public void addRuleInstances(Digester digester) {
            if (!(digester instanceof RulesDigester)) {
                throw new IllegalStateException(
                    "XML rules must be parsed by SecureDigesterLoader");
            }
            RulesDigester rulesDigester = (RulesDigester) digester;
            rulesDigester.registering = this;
            rulesDigester.includeRuleReplaced = false;
            try {
                super.addRuleInstances(digester);
            } finally {
                rulesDigester.registering = null;
            }
            if (!rulesDigester.includeRuleReplaced) {
                throw new IllegalStateException("Commons Digester did not"
                    + " register " + INCLUDE_RULE_CLASS + ", so included rules"
                    + " files could not be parsed securely");
            }
        }

        /**
         * Handles <code>&lt;include&gt;</code> like the Commons Digester rule,
         * but parses an included rules file with a {@link RulesDigester}.
         */
        final class SecureIncludeRule extends Rule {

            /** The Commons Digester rule, used for the class attribute. */
            private final Rule original;

            SecureIncludeRule(Rule original) {
                this.original = original;
            }

            public void begin(String namespace, String name, Attributes attributes)
                    throws Exception {
                String fileName = attributes.getValue("path");
                if (fileName != null && fileName.length() > 0) {
                    includeXMLRules(fileName);
                }

                String className = attributes.getValue("class");
                if (className != null && className.length() > 0) {
                    AttributesImpl classOnly = new AttributesImpl();
                    classOnly.addAttribute("", "class", "class", "CDATA", className);
                    original.begin(namespace, name, classOnly);
                }
            }

            private void includeXMLRules(String fileName) throws Exception {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) {
                    cl = IncludeSafeRuleParser.this.getClass().getClassLoader();
                }
                URL fileURL = cl.getResource(fileName);
                if (fileURL == null) {
                    throw new FileNotFoundException(
                        "File \"" + fileName + "\" not found.");
                }
                fileName = fileURL.toExternalForm();
                if (!includedFiles.add(fileName)) {
                    throw new CircularIncludeException(fileName);
                }
                IncludeSafeRuleParser includedSet = new IncludeSafeRuleParser(
                    targetDigester, patternStack, includedFiles);
                includedSet.setDigesterRulesDTD(getDigesterRulesDTD());
                Digester digester = new RulesDigester();
                digester.addRuleSet(includedSet);
                digester.push(IncludeSafeRuleParser.this);
                digester.parse(fileName);
                includedFiles.remove(fileName);
            }
        }
    }
}
