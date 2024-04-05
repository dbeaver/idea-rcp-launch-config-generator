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

import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2Repository;
import org.jkiss.tools.rcplaunchconfig.util.SystemUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.Pair;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ContentFileHandler extends DefaultHandler {
    private static final Pattern ARCH_PATTERN = Pattern.compile("osgi\\.arch=([^&)]+)");

    private static final Pattern WS_PATTERN = Pattern.compile("osgi\\.ws=([^&)]+)");
    private static final Pattern OS_PATTERN = Pattern.compile("osgi\\.os=([^&)]+)");

    private static final Pattern START_LEVEL_PATTERN = Pattern.compile("startLevel:\\s*(-?\\d+)\n");
    private final RemoteP2Repository repository;
    private final P2BundleLookupCache cache;
    private RemoteP2BundleInfo.RemoteBundleInfoBuilder currentBundle;
    private Pair<String, DependencyType> currentDependency;

    private boolean currentElementValidForOS = true;
    private ContentType currentContentType = null;
    private final Set<RemoteP2BundleInfo> remoteP2BundleInfos = new HashSet<>();

    public static Set<RemoteP2BundleInfo> indexContent(File file, RemoteP2Repository repository, P2BundleLookupCache cache) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true );
        SAXParser saxParser = factory.newSAXParser();
        ContentFileHandler contentFileHandler = new ContentFileHandler(repository, cache);
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

    public Set<RemoteP2BundleInfo> getRemoteBundleInfos() {
        return remoteP2BundleInfos;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if ("unit".equalsIgnoreCase(qName)) {
            currentBundle = new RemoteP2BundleInfo.RemoteBundleInfoBuilder();
            String id = attributes.getValue("id");
            String version = attributes.getValue("version");
            currentBundle.bundleName(id).version(version).repositoryURL(repository);
        }
        if ("required".equalsIgnoreCase(qName) || "provided".equalsIgnoreCase(qName)) {
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
                RemoteP2BundleInfo bundle = currentBundle.build();
                if (repository.bundleIsIndexed(bundle)) {
                    cache.addRemoteBundle(bundle);
                    remoteP2BundleInfos.add(bundle);
                }
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
        if ("provided".equalsIgnoreCase(qName)) {
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
            String level = getMatchOrNull(START_LEVEL_PATTERN, content);
            if (!CommonUtils.isEmpty(level)) {
//                currentBundle.setStartLevel(CommonUtils.toInt(level, 0));
            }
        }
        if (ContentType.FILTER.equals(currentContentType)) {
            String os = getMatchOrNull(OS_PATTERN, content);
            String ws = getMatchOrNull(WS_PATTERN, content);
            String arch = getMatchOrNull(ARCH_PATTERN, content);
            currentElementValidForOS = SystemUtils.matchesDeclaredOS(os, ws, arch);
        }

        super.characters(ch, start, length);
    }

    private String getMatchOrNull(Pattern pattern, String content) {
        if (!pattern.matcher(content).matches()) {
            return null;
        }
        return OS_PATTERN.matcher(content).group(1);
    }

    private ContentFileHandler(RemoteP2Repository repository, P2BundleLookupCache cache) {
        this.repository = repository;
        this.cache = cache;
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
