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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public enum XmlReader {
    INSTANCE();

    private final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

    private final XmlReaderExtension[] EXTENSIONS = new XmlReaderExtension[]{
        new ProjectXmlReaderExtension(),
        new FeatureXmlReaderExtension(),
        new PluginXmlReaderExtension(),
        new LaunchArgumentsXMLReaderExtension()
    };

    public void parseXmlFile(@Nonnull Result result, @Nonnull File xmlFile) throws IOException, XMLStreamException {
        try (var inputStream = new FileInputStream(xmlFile);
             var bufferedInput = new BufferedInputStream(inputStream)
        ) {
            var reader = XML_INPUT_FACTORY.createXMLEventReader(bufferedInput);
            while (reader.hasNext()) {
                var nextEvent = reader.nextEvent();
                if (nextEvent.isStartElement()) {
                    var startElement = nextEvent.asStartElement();
                    for (var extension : EXTENSIONS) {
                        extension.resolveStartElement(result, startElement, reader);
                    }
                }
            }
        }
    }
}
