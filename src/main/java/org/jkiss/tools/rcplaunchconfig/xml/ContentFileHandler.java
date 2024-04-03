package org.jkiss.tools.rcplaunchconfig.xml;

import org.jkiss.tools.rcplaunchconfig.BundleInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContentFileHandler extends DefaultHandler {
    private  RemoteBundleInfo currentBundle;
    private PackageInfo currentPackage;
    // TODO add proper structure
    private List<RemoteBundleInfo> remoteBundleInfos = new ArrayList<>();

    public static List<RemoteBundleInfo> parseContent(File file) throws IOException, SAXException, ParserConfigurationException {
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
            String id = attributes.getValue("id");
            String version = attributes.getValue("version");
            String generation = attributes.getValue("generation");
            currentBundle = new RemoteBundleInfo(id, version, generation);
        }
        if ("required".equalsIgnoreCase(qName) || "exported".equalsIgnoreCase(qName)) {
            String id = attributes.getValue("namespace");
            String name = attributes.getValue("name");
            String version = attributes.getValue("version");
            String range = attributes.getValue("range");
            currentPackage = new PackageInfo(id, name, version, range);
        }
        if ("property".equalsIgnoreCase(qName) && currentPackage != null) {
            String type = attributes.getValue("type");
            currentPackage.setType(type);
        }
        super.startElement(uri, localName, qName, attributes);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if ("unit".equalsIgnoreCase(qName)) {
            remoteBundleInfos.add(currentBundle);
            currentBundle = null;
        }
        if ("required".equalsIgnoreCase(qName)) {
            currentBundle.addRequiredPackage(currentPackage);
        }
        if ("exported".equalsIgnoreCase(qName)) {
            currentBundle.addExportedPackage(currentPackage);
        }

        super.endElement(uri, localName, qName);
    }


    public class RemoteBundleInfo {
        String id;
        String version;
        String generation;

        private final List<PackageInfo> requiredPackages = new ArrayList<>();
        private final List<PackageInfo> exportedPackages = new ArrayList<>();

        public RemoteBundleInfo(String id, String version, String generation) {
            this.id = id;
            this.version = version;
            this.generation = generation;
        }

        public void addRequiredPackage(PackageInfo info) {
            requiredPackages.add(info);
        }

        public void addExportedPackage(PackageInfo info) {
            exportedPackages.add(info);
        }

        public List<PackageInfo> getRequiredPackages() {
            return requiredPackages;
        }

        public List<PackageInfo> getExportedPackages() {
            return exportedPackages;
        }
    }

    public static class PackageInfo {
        private final String version;
        String namespace;
        String name;
        String versionRange;
        boolean optional = false;
        boolean greedy = false;
        String type;

        public PackageInfo(String namespace, String name, String version, String versionRange) {
            this.namespace = namespace;
            this.name = name;
            this.version = version;
            this.versionRange = versionRange;
        }

        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        public void setGreedy(boolean greedy) {
            this.greedy = greedy;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

}
