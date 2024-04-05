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
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.P2RepositoryManager;
import org.jkiss.tools.rcplaunchconfig.resolvers.PluginResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

class PluginXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(PluginXmlReaderExtension.class);

    static void resolvePlugin(@Nonnull Result result, @Nonnull StartElement startElement, @Nonnull Attribute idAttr) {
        var startLevelAttr = startElement.getAttributeByName(START_LEVEL_ATTR_NAME);
        var startLevel = startLevelAttr != null
            ? Integer.parseInt(startLevelAttr.getValue())
            : null;
        try {
            PluginResolver.resolvePluginDependencies(result, idAttr.getValue(), startLevel, P2RepositoryManager.INSTANCE.getLookupCache());
        } catch (IOException e) {
            log.error("Failed to resolve plugin", e);
        }
    }

    @Override
    public void resolveStartElement(@Nonnull Result result, @Nonnull StartElement startElement) {
        if (!matchesDeclaredOS(startElement)) {
            return;
        }

        var nameLocalPart = startElement.getName().getLocalPart();
        Attribute attribute = null;
        switch (nameLocalPart) {
            case "plugin": {
                attribute = startElement.getAttributeByName(ID_ATTR_NAME);
                break;
            }
            case "import": {
                attribute = startElement.getAttributeByName(PLUGIN_ATTR_NAME);
                break;
            }
        }
        if (attribute == null) {
            return;
        }
        resolvePlugin(result, startElement, attribute);
    }
}
