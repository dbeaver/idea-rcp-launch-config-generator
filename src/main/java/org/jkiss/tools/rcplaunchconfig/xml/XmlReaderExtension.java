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

import jakarta.annotation.Nonnull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.Result;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

abstract class XmlReaderExtension {

    protected static final QName ID_ATTR_NAME = new QName("", "id");
    protected static final QName OS_ATTR_NAME = new QName("", "os");
    protected static final QName WS_ATTR_NAME = new QName("", "ws");
    protected static final QName START_LEVEL_ATTR_NAME = new QName("", "startLevel");
    protected static final QName PLUGIN_ATTR_NAME = new QName("", "plugin");
    private static final QName ARCH_ATTR_NAME = new QName("", "arch");

    protected static boolean matchesDeclaredOS(@Nonnull StartElement startElement) {
        Attribute osAttr = startElement.getAttributeByName(OS_ATTR_NAME);
        if (osAttr != null && !osAttr.getValue().equals(BundleInfo.currentOS)) {
            return false;
        }
        Attribute wsAttr = startElement.getAttributeByName(WS_ATTR_NAME);
        if (wsAttr != null && !wsAttr.getValue().equals(BundleInfo.currentWS)) {
            return false;
        }
        Attribute archAttr = startElement.getAttributeByName(ARCH_ATTR_NAME);
        if (archAttr != null && !archAttr.getValue().equals(BundleInfo.currentArch)) {
            return false;
        }
        return true;
    }

    public abstract void resolveStartElement(@Nonnull Result result, @Nonnull StartElement startElement);
}
