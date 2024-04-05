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

import org.jkiss.tools.rcplaunchconfig.RemoteBundleInfo;
import org.jkiss.tools.rcplaunchconfig.utils.OsUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.Pair;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ContentFileHandler extends DefaultHandler {
    private final Pattern ARCH_PATTERN = Pattern.compile("osgi\\.arch=([^&)]+)");

    private final Pattern WS_PATTERN = Pattern.compile("osgi\\.ws=([^&)]+)");
    private final Pattern OS_PATTERN = Pattern.compile("osgi\\.os=([^&)]+)");

    private final Pattern START_LEVEL_PATTERN = Pattern.compile("startLevel:\\s*(-?\\d+)\n");
    private RemoteBundleInfo.RemoteBundleInfoBuilder currentBundle;
    private Pair<String, DependencyType> currentDependency;

    private boolean currentElementValidForOS = true;
    private ContentType currentContentType = null;

    // TODO add proper structure
    private List<RemoteBundleInfo> remoteBundleInfos = new ArrayList<>();

    public static List<RemoteBundleInfo> indexContent(File file, URL url) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true );

        SAXParser saxParser = factory.newSAXParser();
        ContentFileHandler contentFileHandler = new ContentFileHandler();
        saxParser.parse(file, contentFileHandler);
        return contentFileHandler.getRemoteBundleInfos();
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
    }

    public List<RemoteBundleInfo> getRemoteBundleInfos() {
        return remoteBundleInfos;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ("unit".equalsIgnoreCase(qName)) {
            currentBundle = new RemoteBundleInfo.RemoteBundleInfoBuilder();
            String id = attributes.getValue("id");
            String version = attributes.getValue("version");
            currentBundle.bundleName(id).version(version);
        }
        if ("required".equalsIgnoreCase(qName) || "exported".equalsIgnoreCase(qName)) {
            String namespace = attributes.getValue("namespace");
            DependencyType type;
            if ("java.package".equalsIgnoreCase(namespace)) {
                type = DependencyType.PLUGIN;
            } else if ("osgi.bundle".equalsIgnoreCase(namespace)) {
                type = DependencyType.BUNDLE;
            } else {
                return;
            }
            String name = attributes.getValue("name");
            currentDependency = new Pair<>(name, type);
        }
        if (currentBundle != null && "instruction".equalsIgnoreCase(qName) && "configure".equalsIgnoreCase(attributes.getValue("key"))) {
            currentContentType = ContentType.INSTRUCTION;
        }
        if (currentBundle != null && "filter".equalsIgnoreCase(qName)) {
            currentContentType = ContentType.FILTER;
        }

        super.startElement(uri, localName, qName, attributes);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("unit".equalsIgnoreCase(qName)) {
            if (currentElementValidForOS) {
                remoteBundleInfos.add(currentBundle.build());
            }
            currentBundle = null;
            currentElementValidForOS = true;
        }
        if ("required".equalsIgnoreCase(qName)) {
            if (currentDependency == null) {
                return;
            }
            if (currentElementValidForOS) {
                if (currentDependency.getSecond().equals(DependencyType.BUNDLE)) {
                    currentBundle.addToRequiredBundles(currentDependency.getFirst());
                } else {
                    currentBundle.addToRequiredPackages(currentDependency.getFirst());
                }
            }
            currentDependency = null;
            currentElementValidForOS = true;
        }
        if ("exported".equalsIgnoreCase(qName)) {
            if (currentDependency == null) {
                return;
            }
            if (currentElementValidForOS) {
                currentBundle.addToExportPackage(currentDependency.getFirst());
            }
            currentDependency = null;
            currentElementValidForOS = true;
        }
        if (currentBundle != null && "instruction".equalsIgnoreCase(qName) && ContentType.INSTRUCTION.equals(currentContentType)) {
            currentContentType = null;
        }
        if (currentBundle != null && "filter".equalsIgnoreCase(qName) && ContentType.FILTER.equals(currentContentType)) {
            currentContentType = null;
        }
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String content = new String(ch, start, length);
        if (ContentType.INSTRUCTION.equals(currentContentType)) {
            String level = START_LEVEL_PATTERN.matcher(content).group(1);
            if (!CommonUtils.isEmpty(level)) {
                currentBundle.setStartLevel(CommonUtils.toInt(level, 0));
            }
        }
        if (ContentType.FILTER.equals(currentContentType)) {
            String os = OS_PATTERN.matcher(content).group(1);
            String ws = WS_PATTERN.matcher(content).group(1);
            String arch = ARCH_PATTERN.matcher(content).group(1);
            currentElementValidForOS = OsUtils.matchesDeclaredOS(os, ws, arch);
        }

        super.characters(ch, start, length);
    }

    private enum DependencyType {
        PLUGIN,
        BUNDLE
    }

    private enum ContentType {
        INSTRUCTION,
        FILTER
    }
}
