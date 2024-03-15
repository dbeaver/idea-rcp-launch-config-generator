/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp
 *
 * All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of DBeaver Corp and its suppliers, if any.
 * The intellectual and technical concepts contained
 * herein are proprietary to DBeaver Corp and its suppliers
 * and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from DBeaver Corp.
 */
package org.jkiss.tools.rcplaunchconfig.xml;

import jakarta.annotation.Nonnull;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.resolvers.PluginResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.io.IOException;

class PluginXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(PluginXmlReaderExtension.class);

    static void resolvePlugin(@Nonnull Result result, @Nonnull StartElement startElement, @Nonnull Attribute idAttr) {
        var startLevelAttr = startElement.getAttributeByName(START_LEVEL_ATTR_NAME);
        var startLevel = startLevelAttr != null
            ? Integer.parseInt(startLevelAttr.getValue())
            : null;
        try {
            PluginResolver.resolvePluginDependencies(result, idAttr.getValue(), startLevel);
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
