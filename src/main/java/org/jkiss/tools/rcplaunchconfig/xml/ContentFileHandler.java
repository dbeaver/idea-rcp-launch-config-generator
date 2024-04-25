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

import org.jkiss.code.NotNull;
import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.P2BundleLookupCache;
import org.jkiss.tools.rcplaunchconfig.p2.RemoteP2Feature;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2BundleInfo;
import org.jkiss.tools.rcplaunchconfig.p2.repository.RemoteP2Repository;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.Pair;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ContentFileHandler extends DefaultHandler {


    private final RemoteP2Repository repository;
    private final P2BundleLookupCache cache;
    private RemoteP2BundleInfo.RemoteBundleInfoBuilder currentBundle;
    private Pair<String, DependencyType> currentDependency;

    private ParserState currentState = ParserState.ROOT;
    private ContentType currentContentType = null;
    private final Set<RemoteP2BundleInfo> remoteP2BundleInfos = new HashSet<>();

    private final Set<RemoteP2Feature> remoteP2Features = new HashSet<>();
    private UnitInformation currentUnit;
    private String artifactID;

    public static void indexContent(
            @NotNull RemoteP2Repository repository,
            @NotNull File contentFile,
            @NotNull P2BundleLookupCache cache
    ) throws IOException, SAXException, ParserConfigurationException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        SAXParser saxParser = factory.newSAXParser();
        ContentFileHandler contentFileHandler = new ContentFileHandler(repository, cache);
        saxParser.parse(contentFile, contentFileHandler);
        repository.addRemoteBundles(contentFileHandler.remoteP2BundleInfos);
        repository.addRemoteFeatures(contentFileHandler.remoteP2Features);
    }


    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
    }

    @Override
    public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) throws SAXException {
         if (ContentFileConstants.UNIT_KEYWORD.equalsIgnoreCase(qualifiedName)) {
            currentState = ParserState.PLUGIN_VALID;
            String id = attributes.getValue(ContentFileConstants.ID_FIELD);
            String version = attributes.getValue(ContentFileConstants.VERSION_FIELD);
            this.currentUnit = new UnitInformation(id, version);
        }
        if (currentState.isInsideUnit() && ContentFileConstants.PROPERTY_KEYWORD.equalsIgnoreCase(qualifiedName)
            && "maven-artifactId".equalsIgnoreCase(attributes.getValue(ContentFileConstants.NAME_FIELD))) {
            artifactID = attributes.getValue(ContentFileConstants.FIELD_VALUE);
        }
        if (
            currentState.equals(ParserState.PLUGIN_VALID) && ContentFileConstants.PROPERTY_KEYWORD.equalsIgnoreCase(qualifiedName)
                && ContentFileConstants.MAVEN_TYPE_FIELD.equalsIgnoreCase(attributes.getValue(ContentFileConstants.NAME_FIELD))
        ) {
            if ("eclipse-feature".equalsIgnoreCase(attributes.getValue(ContentFileConstants.FIELD_VALUE))
            ) {
                currentState = ParserState.FEATURE_VALID;
            } else if ("jar".equalsIgnoreCase(attributes.getValue(ContentFileConstants.FIELD_VALUE))
                || "eclipse-plugin".equalsIgnoreCase(attributes.getValue(ContentFileConstants.FIELD_VALUE))
                || "java-source".equalsIgnoreCase(attributes.getValue(ContentFileConstants.FIELD_VALUE))
            ) {
                if ("java-source".equalsIgnoreCase(attributes.getValue(ContentFileConstants.FIELD_VALUE))) {
                    currentState = ParserState.SOURCES_VALID;
                    initBundle(true);
                } else {
                    currentState = ParserState.PLUGIN_VALID;
                    initBundle(false);
                }

            }
        }
        if (
            currentState.isPluginOrComment()
                && (ContentFileConstants.REQUIRED_KEYWORD.equalsIgnoreCase(qualifiedName)
                || ContentFileConstants.PROVIDED_KEYWORD.equalsIgnoreCase(qualifiedName))
        ) {
            if (
                ContentFileConstants.REQUIRED_KEYWORD.equalsIgnoreCase(qualifiedName)
                    && "true".equalsIgnoreCase(attributes.getValue("optional"))
            ) {
                currentState = ParserState.DEPENDENCY_INVALID;
                return;
            }
            String name = attributes.getValue(ContentFileConstants.NAME_FIELD);
            String namespace = attributes.getValue(ContentFileConstants.NAMESPACE_FIELD);
            DependencyType type = DependencyType.getType(namespace);
            currentState = ParserState.DEPENDENCY;
            if (currentBundle == null) {
                initBundle(false);
            }
            currentDependency = new Pair<>(name, type);
        }
        if (!currentState.isInvalid()
            && currentState.isInsideUnit()
            && ContentFileConstants.INSTRUCTION_KEYWORD.equalsIgnoreCase(qualifiedName)) {
            if ("configure".equalsIgnoreCase(attributes.getValue(ContentFileConstants.KEY_FIELD))) {
                currentContentType = ContentType.INSTRUCTION;
            } else if (currentState.isPluginOrComment()
                && "zipped".equals(attributes.getValue(ContentFileConstants.KEY_FIELD))) {
                currentBundle.setZipped(true);
            }
        }
        if (
            !currentState.isInvalid()
                && (currentState.isInsideUnit() || currentState.isInsideDependency())
                && ContentFileConstants.FILTER_KEYWORD.equalsIgnoreCase(qualifiedName)
        ) {
            currentContentType = ContentType.FILTER;
        }

        super.startElement(uri, localName, qualifiedName, attributes);
    }

    private void initBundle(boolean sourceBundle) {
        currentBundle = new RemoteP2BundleInfo.RemoteBundleInfoBuilder();
        String bundleName = sourceBundle ? currentUnit.id + ".source" : currentUnit.id;
        currentBundle.bundleName(currentUnit.id).version(currentUnit.version()).repositoryURL(repository);
    }

    @Override
    public void endElement(String uri, String localName, String qualifiedName) throws SAXException {
        if (currentState.isInsideUnit() && ContentFileConstants.UNIT_KEYWORD.equalsIgnoreCase(qualifiedName)) {
            if (currentState != ParserState.UNIT_INVALID) {
                if (currentState.isPluginOrComment()) {
                    if (currentBundle == null) {
                        initBundle(false);
                    }
                    RemoteP2BundleInfo bundle = currentBundle.build();
                    if (repository.isIndexed(bundle.getBundleName(), bundle.getBundleVersion())) {
                        cache.addRemoteBundle(bundle);
                        remoteP2BundleInfos.add(bundle);
                    }
                }
                if (currentState == ParserState.FEATURE_VALID) {
                    RemoteP2Feature remoteP2Feature = new RemoteP2Feature(artifactID, currentUnit.version(), repository);
                    if (repository.isIndexed(artifactID, currentUnit.version())) {
                        cache.addRemoteFeature(remoteP2Feature);
                        remoteP2Features.add(remoteP2Feature);
                    }
                }
            }
            currentBundle = null;
            currentUnit = null;
            artifactID = null;
            currentState = ParserState.ROOT;
        }
        if (currentState.isInsideDependency() && ContentFileConstants.REQUIRED_KEYWORD.equalsIgnoreCase(qualifiedName)) {
            if (currentState != ParserState.DEPENDENCY_INVALID) {
                if (currentDependency.getSecond().equals(DependencyType.BUNDLE)) {
                    currentBundle.addToRequiredBundles(currentDependency.getFirst());
                } else if (currentDependency.getSecond().equals(DependencyType.PLUGIN)) {
                    currentBundle.addToRequiredPackages(currentDependency.getFirst());
                }
            }
            currentDependency = null;
            currentState = ParserState.PLUGIN_VALID;
        }

        if (currentState.isInsideDependency() && ContentFileConstants.PROVIDED_KEYWORD.equalsIgnoreCase(qualifiedName)) {
            if (currentState != ParserState.DEPENDENCY_INVALID) {
                if (
                    currentDependency.getSecond().equals(DependencyType.PLUGIN)
                ) {
                    currentBundle.addToExportPackage(currentDependency.getFirst());
                }
            }
            currentDependency = null;
            currentState = ParserState.PLUGIN_VALID;
        }
        if (currentState.isInsideDependency()
            && ContentFileConstants.REQUIRED_PROPERTIES_KEYWORD.equalsIgnoreCase(qualifiedName)) {
            if (currentState != ParserState.DEPENDENCY_INVALID) {
                currentBundle.addToRequiredPackages(currentDependency.getFirst());
            }
            currentDependency = null;
            currentState = ParserState.PLUGIN_VALID;
        }
        if (
            currentState.isInsideUnit()
                && ContentFileConstants.INSTRUCTION_KEYWORD.equalsIgnoreCase(qualifiedName)
                && ContentType.INSTRUCTION.equals(currentContentType)
        ) {
            currentContentType = null;
        }
        if ((currentState.isInsideUnit() || currentState.isInsideDependency())
            && ContentFileConstants.FILTER_KEYWORD.equalsIgnoreCase(qualifiedName) && ContentType.FILTER.equals(currentContentType)) {
            currentContentType = null;
        }
        super.endElement(uri, localName, qualifiedName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        String content = new String(ch, start, length);
        if (!currentState.isInvalid() && ContentType.INSTRUCTION.equals(currentContentType)) {
            String level = getMatchOrNull(ContentFileConstants.START_LEVEL_PATTERN, content.trim());
            if (!CommonUtils.isEmpty(level)) {
                currentBundle.setStartLevel(Integer.parseInt(level));
            }
        }
        if (ContentType.FILTER.equals(currentContentType)) {
            String filter = content.trim();
            if (!FilterEvaluator.parseEval(filter)) {
                currentState = currentState.isInsideDependency() ? ParserState.DEPENDENCY_INVALID : ParserState.UNIT_INVALID;
            }
        }

        super.characters(ch, start, length);
    }

    private String getMatchOrNull(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private ContentFileHandler(RemoteP2Repository repository, P2BundleLookupCache cache) {
        this.repository = repository;
        this.cache = cache;
    }


    private enum ParserState {
        ROOT, // ROOT -> FEATURE_VALID | PLUGIN_VALID
        FEATURE_VALID, // FEATURE_VALID -> UNIT_INVALID | ROOT
        PLUGIN_VALID, // PLUGIN_VALID -> UNIT_INVALID | DEPENDENCY
        SOURCES_VALID,
        DEPENDENCY, //  PLUGIN_IMPORT
        DEPENDENCY_INVALID, // DEPENDENCY_INVALID -> PLUGIN_VALID
        UNIT_INVALID; // UNIT_INVALID -> ROOT

        private boolean isInsideDependency() {
            return this.equals(DEPENDENCY) || this.equals(DEPENDENCY_INVALID);
        }

        private boolean isInsideUnit() {
            return this.equals(FEATURE_VALID) || isPluginOrComment()|| this.equals(UNIT_INVALID);
        }

        private boolean isPluginOrComment() {
            return this.equals(PLUGIN_VALID) || this.equals(SOURCES_VALID);
        }

        private boolean isInvalid() {
            return this.equals(DEPENDENCY_INVALID) || this.equals(UNIT_INVALID);
        }
    }

    private record UnitInformation(String id, String version) {
    }

    private enum DependencyType {
        PLUGIN,
        BUNDLE,
        UNKNOWN;

        public static final String OSGI_SERVICELOADER = "osgi.serviceloader";
        public static final String OSGI_BUNDLE = "osgi.bundle";
        public static final String JAVA_PACKAGE = "java.package";

        public static DependencyType getType(String namespace) {
            DependencyType type;
            if (JAVA_PACKAGE.equalsIgnoreCase(namespace)) {
                type = DependencyType.PLUGIN;
            } else if (OSGI_BUNDLE.equalsIgnoreCase(namespace)) {
                type = DependencyType.BUNDLE;
            } else {
                type = DependencyType.UNKNOWN;
            }
            return type;
        }
    }

    private enum ContentType {
        INSTRUCTION,
        FILTER
    }

    private class FilterEvaluator {
        public static boolean parseEval(String filter) {
            if (filter.length() <= 1) {
                return true;
            }
            char[] filterArray = filter.toCharArray();
            return parseSubCondition(filterArray, 0);
        }

        private static boolean parseSubCondition(char[] filterArray, int i) {
            Stack<Character> currentGroup = new Stack<>();
            currentGroup.push('(');
            int j = i + 1;
            StringBuilder currentCondition = new StringBuilder();
            boolean orCondition = false;
            Boolean isTrue = null;
            //(&amp;(osgi.os=macosx)(|(osgi.arch=aarch64)(osgi.arch=x86_64)))

            while (!currentGroup.isEmpty()) {
                char c = filterArray[j];
                if (c == '(') {
                    if (currentGroup.size() == 1) {
                        if (!currentCondition.isEmpty()) {
                            String condition = currentCondition.toString();
                            switch (condition) {
                                case "&amp;":
                                    break;
                                case "|":
                                    orCondition = true;
                                    break;
                                default:
                                    return evalSimpleCondition(condition);
                            }
                            currentCondition = new StringBuilder();
                        }
                        if (isTrue == null) {
                            isTrue = parseSubCondition(filterArray, j);
                        } else {
                            if (orCondition) {
                                isTrue |= parseSubCondition(filterArray, j);
                            } else {
                                isTrue &= parseSubCondition(filterArray, j);
                            }
                        }
                    }
                    currentGroup.push('(');
                } else if (c == ')') {
                    if (!currentCondition.isEmpty()) {
                        String condition = currentCondition.toString();
                        switch (condition) {
                            case "&amp;":
                                break;
                            case "|":
                                orCondition = true;
                                break;
                            default:
                                return evalSimpleCondition(condition);
                        }
                        currentCondition = new StringBuilder();
                    }
                    currentGroup.pop();
                } else if (currentGroup.size() == 1) {
                    currentCondition.append(filterArray[j]);
                }
                j++;
            }
            return isTrue == null | Boolean.TRUE.equals(isTrue);
        }
        private static boolean evalSimpleCondition(String condition) {
            String[] split = condition.split("=");
            if (split.length != 2) {
                return true;
            }
            String parameter = split[0];
            String value = split[1];
            switch (parameter) {
                case ContentFileConstants.OS_FILTER -> {
                    return value.equals(BundleInfo.currentOS);
                }
                case ContentFileConstants.WS_FILTER -> {
                    return value.equals(BundleInfo.currentWS);
                }
                case ContentFileConstants.ARCH_FILTER -> {
                    return value.equals(BundleInfo.currentArch);
                }
            }
            return true;
        }

    }
}
