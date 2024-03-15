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
import org.jkiss.tools.rcplaunchconfig.resolvers.FeatureResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import java.io.IOException;

class FeatureXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(FeatureXmlReaderExtension.class);

    static void resolveFeature(@Nonnull Result result, @Nonnull StartElement startElement) {
        var attribute = startElement.getAttributeByName(ID_ATTR_NAME);
        if (attribute != null) {
            try {
                FeatureResolver.resolveFeatureDependencies(result, attribute.getValue());
            } catch (IOException | XMLStreamException e) {
                log.error("Failed to resolve feature", e);
            }
        }
    }

    @Override
    public void resolveStartElement(@Nonnull Result result, @Nonnull StartElement startElement) {
        var nameLocalPart = startElement.getName().getLocalPart();
        if (nameLocalPart.equals("includes") || nameLocalPart.equals("feature")) {
            resolveFeature(result, startElement);
        }
    }
}
