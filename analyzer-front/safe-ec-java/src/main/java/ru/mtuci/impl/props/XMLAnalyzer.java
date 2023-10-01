package ru.mtuci.impl.props;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import ru.mtuci.base.LocationMeta;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.utils.Either;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class XMLAnalyzer extends RequestingAnalyzer
{
    public static final List<String> EXTENSIONS = List.of(".xml");
    public static final List<String> MIME_TYPES = List.of("application/xml", "text/xml");

    public XMLAnalyzer(Path path)
    {
        super(path);
    }

    @Override
    @SneakyThrows
    protected List<RequestDto> makeRequests()
    {
        Properties props = new Properties();
        BiConsumer<LocationMeta.LocationMetaBuilder, String> propFiller;
        try (var is = Files.newInputStream(path))
        {
            props.loadFromXML(is);
            propFiller = LocationMeta.LocationMetaBuilder::property;
        }
        catch (Exception e)
        {
            try (var is = Files.newInputStream(path))
            {
                props.clear();
                var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                var root = document.getDocumentElement();
                var result = new HashMap<String, Pair<String, String>>();
                flattenXml("", "", null, root, result);
                result.forEach((prop, xpathAndVal) -> props.put(prop, xpathAndVal.getRight()));
                propFiller = (meta, prop) -> meta.xpath(result.get(prop).getLeft());
            }
        }

        BiConsumer<LocationMeta.LocationMetaBuilder, String> finalPropFiller = propFiller;
        return PropertiesAnalyzer.analyzeProps(props, m -> {
            var resp = request(m.toRequest());
            return Either.left(new RequestDto(resp, () -> {
                var builder = baseMeta();
                finalPropFiller.accept(builder, m.prop());
                return builder.build();
            }));
        });
    }

    private static void flattenXml(String currentPath, String currentXpath, Integer index, Node currentNode, Map<String, Pair<String, String>> result)
    {
        if (currentNode.getNodeType() == Node.TEXT_NODE && !currentNode.getNodeValue().trim().isEmpty())
        {
            result.put(currentPath, Pair.of(currentXpath, currentNode.getNodeValue().trim()));
        }
        else if (currentNode.getNodeType() != Node.TEXT_NODE)
        {
            NodeList childNodes = currentNode.getChildNodes();
            int length = childNodes.getLength();
            var currentNodeName = nodeName(currentNode);
            String nextPath = currentPath.isEmpty() ? currentNodeName : currentPath + "." + currentNodeName;
            String nextXpath = currentXpath.isEmpty() ? "/" + currentNodeName : currentXpath + "/" + currentNodeName;
            if (index != null)
            {
                var indexPath = "[" + index + "]";
                nextPath += indexPath;
                nextXpath += indexPath;
            }

            Map<String, Integer> times = new HashMap<>();
            for (int i = 0; i < childNodes.getLength(); i++)
            {
                times.compute(nodeName(childNodes.item(i)), (__, val) -> {
                    if (val == null)
                        return 1;

                    return val + 1;
                });
            }

            Map<String, Integer> indexes = times.entrySet().stream().filter(e -> e.getValue() > 1).collect(Collectors.toMap(Map.Entry::getKey, __ -> 0));
            for (int i = 0; i < length; i++)
            {
                var item = childNodes.item(i);
                var itemName = nodeName(item);
                var itemIndex = indexes.get(itemName);
                flattenXml(nextPath, nextXpath, itemIndex, item, result);

                if (itemIndex != null)
                   indexes.put(itemName, itemIndex + 1);
            }
        }
    }

    private static String nodeName(Node node)
    {
        return StringUtils.firstNonEmpty(node.getLocalName(), node.getNodeName());
    }
}
