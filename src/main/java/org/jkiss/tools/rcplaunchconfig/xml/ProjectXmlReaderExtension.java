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
import org.jkiss.tools.rcplaunchconfig.PathsManager;
import org.jkiss.tools.rcplaunchconfig.Result;
import org.jkiss.tools.rcplaunchconfig.util.DependencyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.StartElement;
import java.nio.file.Files;
import java.nio.file.Path;

class ProjectXmlReaderExtension extends XmlReaderExtension {

    private static final Logger log = LoggerFactory.getLogger(ProjectXmlReaderExtension.class);

    private static final QName LOCATION_NAME = new QName("", "location");

    @Override
    public void resolveStartElement(@Nonnull Result result, @Nonnull StartElement startElement, XMLEventReader reader,
                                    DependencyGraph graph) {
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
                FeatureXmlReaderExtension.resolveFeature(result, startElement, graph);
                break;
            }
            case "plugin": {
                var idAttr = startElement.getAttributeByName(ID_ATTR_NAME);
                if (idAttr != null) {
                    PluginXmlReaderExtension.resolvePlugin(result, startElement, idAttr, graph);
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
