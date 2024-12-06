/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.tools.rcplaunchconfig.xml;

import java.util.regex.Pattern;

public class ContentFileConstants {
    public static final String ARCH_FILTER = "osgi.arch";

    public static final String WS_FILTER = "osgi.ws";
    public static final String OS_FILTER = "osgi.os";

    public static final Pattern START_LEVEL_PATTERN = Pattern.compile(".*startLevel:\\s*(-?\\d+).*");
    public static final String REQUIRED_PROPERTIES_KEYWORD = "requiredProperties";
    public static final String INSTRUCTION_KEYWORD = "instruction";
    public static final String PROVIDED_KEYWORD = "provided";
    public static final String FILTER_KEYWORD = "filter";
    public static final String REQUIRED_KEYWORD = "required";
    public static final String UNIT_KEYWORD = "unit";
    public static final String VERSION_FIELD = "version";
    public static final String FIELD_VALUE = "value";
    public static final String ID_FIELD = "id";
    public static final String NAMESPACE_FIELD = "namespace";
    public static final String RANGE_FIELD = "range";
    public static final String NAME_FIELD = "name";
    public static final String MATCH_FIELD = "match";
    public static final String KEY_FIELD = "key";
    public static final String MAVEN_TYPE_FIELD = "maven-type";

    public static final String MAVEN_P2TYPE_CATEGORY = "org.eclipse.equinox.p2.type.category";
    public static final String PROPERTY_KEYWORD = "property";
}
