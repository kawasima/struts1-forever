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
package org.apache.struts.validator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.Arg;
import org.apache.commons.validator.Field;
import org.apache.commons.validator.ValidatorResources;
import org.apache.struts.util.SecureDigesterLoader;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * <p>Loads <code>ValidatorResources</code> from validation files with the
 * XML parsing policy of {@link org.apache.struts.util.DigesterSecurity}.</p>
 *
 * <p>The <code>ValidatorResources(InputStream[])</code> constructor of
 * Commons Validator parses with a <code>Digester</code> that it creates
 * internally and that reads any external DTD or entity a file names. This
 * class does what that constructor does in Commons Validator 1.7 (the same
 * digester rules, DTD registrations and <code>arg0</code>-<code>arg3</code>
 * rules) through public API only, and parses with a
 * <code>SecureDigester</code>. <code>TestValidatorResourcesLoader</code>
 * compares the result with the constructor for its test files and checks
 * the DTD list against Commons Validator; review this class when upgrading
 * Commons Validator.</p>
 */
final class ValidatorResourcesLoader {

    private static final Log log = LogFactory.getLog(ValidatorResources.class);

    /** Digester rules of Commons Validator, next to ValidatorResources. */
    private static final String VALIDATOR_RULES = "digester-rules.xml";

    /** The DTDs that Commons Validator 1.7 registers. */
    static final String[] REGISTRATIONS = {
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.0//EN",
        "/org/apache/commons/validator/resources/validator_1_0.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.0.1//EN",
        "/org/apache/commons/validator/resources/validator_1_0_1.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.1//EN",
        "/org/apache/commons/validator/resources/validator_1_1.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.1.3//EN",
        "/org/apache/commons/validator/resources/validator_1_1_3.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.2.0//EN",
        "/org/apache/commons/validator/resources/validator_1_2_0.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.3.0//EN",
        "/org/apache/commons/validator/resources/validator_1_3_0.dtd",
        "-//Apache Software Foundation//DTD Commons Validator Rules Configuration 1.4.0//EN",
        "/org/apache/commons/validator/resources/validator_1_4_0.dtd"
    };

    private static final String ARGS_PATTERN = "form-validation/formset/form/field/arg";

    private ValidatorResourcesLoader() {
    }

    /**
     * Same as <code>new ValidatorResources(streams)</code>.
     *
     * @param streams validation files, parsed in this order
     * @return the loaded and processed resources
     * @throws IOException if a file cannot be read
     * @throws SAXException if a file cannot be parsed
     */
    static ValidatorResources load(InputStream[] streams)
            throws IOException, SAXException {
        ValidatorResources resources = new ValidatorResources();
        Digester digester = createDigester();
        for (int i = 0; i < streams.length; i++) {
            if (streams[i] == null) {
                throw new IllegalArgumentException("Stream[" + i + "] is null");
            }
            digester.push(resources);
            digester.parse(streams[i]);
        }
        resources.process();
        return resources;
    }

    private static Digester createDigester() {
        URL rulesUrl = ValidatorResources.class.getResource(VALIDATOR_RULES);
        if (log.isDebugEnabled()) {
            log.debug("Loading rules from '" + rulesUrl + "'");
        }
        Digester digester = SecureDigesterLoader.createDigester(rulesUrl);
        digester.setNamespaceAware(true);
        digester.setValidating(true);
        digester.setUseContextClassLoader(true);

        addOldArgRules(digester);

        for (int i = 0; i < REGISTRATIONS.length; i += 2) {
            URL url = ValidatorResources.class.getResource(REGISTRATIONS[i + 1]);
            if (url != null) {
                digester.register(REGISTRATIONS[i], url.toString());
            }
        }
        return digester;
    }

    /** Rules for the <code>arg0</code>-<code>arg3</code> elements. */
    private static void addOldArgRules(Digester digester) {
        Rule rule = new Rule() {
            public void begin(String namespace, String name, Attributes attributes)
                    throws Exception {
                Arg arg = new Arg();
                arg.setKey(attributes.getValue("key"));
                arg.setName(attributes.getValue("name"));
                if ("false".equalsIgnoreCase(attributes.getValue("resource"))) {
                    arg.setResource(false);
                }
                try {
                    arg.setPosition(Integer.parseInt(name.substring("arg".length())));
                } catch (Exception ex) {
                    log.error("Error parsing Arg position: " + name + " " + arg + " " + ex);
                }
                ((Field) getDigester().peek(0)).addArg(arg);
            }
        };
        digester.addRule(ARGS_PATTERN + "0", rule);
        digester.addRule(ARGS_PATTERN + "1", rule);
        digester.addRule(ARGS_PATTERN + "2", rule);
        digester.addRule(ARGS_PATTERN + "3", rule);
    }
}
