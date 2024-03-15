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
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.events.StartElement;
import java.nio.file.Files;
import java.nio.file.Path;

class ProjectXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(ProjectXmlReaderExtension.class);

    private static final QName LOCATION_NAME = new QName("", "location");

    @Override
    public void resolveStartElement(@Nonnull Result result, @Nonnull StartElement startElement) {
        if (!matchesDeclaredOS(startElement)) {
            return;
        }
        var nameLocalPart = startElement.getName().getLocalPart();
        switch (nameLocalPart) {
            case "product": {
                result.setProductInfo(
                    startElement.getAttributeByName(new QName("", "name")).getValue(),
                    startElement.getAttributeByName(new QName("", "uid")).getValue(),
                    startElement.getAttributeByName(new QName("", "id")).getValue(),
                    startElement.getAttributeByName(new QName("", "application")).getValue());
                break;
            }
            case "feature": {
                FeatureXmlReaderExtension.resolveFeature(result, startElement);
                break;
            }
            case "plugin": {
                var idAttr = startElement.getAttributeByName(ID_ATTR_NAME);
                if (idAttr != null) {
                    PluginXmlReaderExtension.resolvePlugin(result, startElement, idAttr);
                }
                break;
            }
            case "splash": {
                var locationAttr = startElement.getAttributeByName(LOCATION_NAME);
                if (locationAttr == null) {
                    log.warn("Invalid 'splash' tag: {}", startElement);
                    break;
                }
                for (Path bundlePath : PathsManager.INSTANCE.getBundlesLocations()) {
                    Path splashPath = bundlePath.resolve(locationAttr.getValue());
                    if (Files.exists(splashPath)) {
                        result.setOsgiSplashPath(splashPath);
                        break;
                    }
                }
                break;
            }
        }
    }
}
