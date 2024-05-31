package org.jkiss.tools.rcplaunchconfig.xml;

import org.jkiss.tools.rcplaunchconfig.Result;

import javax.xml.stream.events.StartElement;

import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaunchArgumentsXMLReaderExtension extends XmlReaderExtension {
    // regex to match CLI args
    public static final Pattern CLI_REGEX = Pattern.compile("(?<=\\s|^)-{1,2}\\S*(?:\\s+[^\\s-]\\S*)?");

    @Override
    public void resolveStartElement(Result result, StartElement startElement, XMLEventReader reader) throws XMLStreamException {
        if ("vmArgs".equals(startElement.getName().getLocalPart())) {
            if (result.getArguments() == null) {
                result.setArguments(new Result.ProductLaunchArguments());
            }
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setVmARGS(strings);
        }
        if ("vmArgsMac".equals(startElement.getName().getLocalPart())) {
            if (result.getArguments() == null) {
                result.setArguments(new Result.ProductLaunchArguments());
            }
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setVmARGSMac(strings);
        }
        if ("programArgs".equals(startElement.getName().getLocalPart())) {
            if (result.getArguments() == null) {
                result.setArguments(new Result.ProductLaunchArguments());
            }
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setProgramARGS(strings);
        }
        if ("programArgsMac".equals(startElement.getName().getLocalPart())) {
            if (result.getArguments() == null) {
                result.setArguments(new Result.ProductLaunchArguments());
            }
            String[] strings = extractArgs(reader, startElement.getName().getLocalPart());
            result.getArguments().setGetProgramARGSMacOS(strings);
        }
    }

    private String[] extractArgs(XMLEventReader reader, String startElement) throws XMLStreamException {
        // Assuming Result has a method to add VM arguments
        StringBuilder args = new StringBuilder();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isEndElement() && startElement.equals(event.asEndElement().getName().getLocalPart())) {
                break;
            }
            if (event.isCharacters()) {
                args.append(event.asCharacters().getData().trim()).append(" ");
            }
        }
        Matcher matcher = CLI_REGEX.matcher(args);

        List<String> argsList = new ArrayList<>();

        while (matcher.find()) {
            argsList.add(matcher.group());
        }
        return argsList.toArray(new String[0]);
    }
}

