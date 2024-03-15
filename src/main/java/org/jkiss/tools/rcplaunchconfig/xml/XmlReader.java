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
        new PluginXmlReaderExtension()
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
                        extension.resolveStartElement(result, startElement);
                    }
                }
            }
        }
    }
}
