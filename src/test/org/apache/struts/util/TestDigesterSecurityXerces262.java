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

/**
 * The XML parsing policy with Apache Xerces 2.6.2, without secure processing; its limit comes from the security manager Struts installs.
 */
public class TestDigesterSecurityXerces262 extends XercesPolicyTestCase {

    protected String version() {
        return "2.6.2";
    }
}
