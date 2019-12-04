import java.util.Comparator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.daisy.common.stax.XMLStreamWriterHelper;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

public class Renderer {

	private static final String RESET_CSS = Renderer.class.getResource("html5-reset.css").toString();
	private static final String SEMANTIC_CSS = Renderer.class.getResource("html5-semantic.css").toString();

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final QName HTML = new QName(HTML_NS, "html");
	private static final QName HEAD = new QName(HTML_NS, "head");
	private static final QName LINK = new QName(HTML_NS, "link");
	private static final QName DIV = new QName(HTML_NS, "div");
	private static final QName SPAN = new QName(HTML_NS, "span");
	private static final Map<QName,String> LINK_ATTRS = ImmutableMap.of(new QName("rel"), "stylesheet",
	                                                                    new QName("type"), "text/css");
	private static final QName HREF = new QName("href");
	private static final QName STYLE = new QName("style");
	private static final QName CLASS = new QName("class");

	public static void render(XMLStreamWriter writer, Box box, boolean preserveStyle) {
		try {
			XMLStreamWriterHelper.writeStartElement(writer, HTML);
			XMLStreamWriterHelper.writeStartElement(writer, HEAD);
			XMLStreamWriterHelper.writeStartElement(writer, LINK);
			XMLStreamWriterHelper.writeAttributes(writer, LINK_ATTRS);
			XMLStreamWriterHelper.writeAttribute(writer, HREF, preserveStyle ? RESET_CSS : SEMANTIC_CSS);
			writer.writeEndElement();
			writer.writeEndElement();
			render(writer, box, null, true, preserveStyle);
			writer.writeEndElement();
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean render(XMLStreamWriter writer,
	                              Box box,
	                              BoxProperties parentBox,
	                              boolean renderingWillStartNewBlock,
	                              boolean preserveStyle) throws XMLStreamException {
		String styleAttr = null;
		if (preserveStyle)
			styleAttr = serializeCascadedProperties(((BoxPropertiesImpl)box.props()).relativize((BoxPropertiesImpl)parentBox));
		boolean skippedStartElement = false;
		if (box.isAnonymous() || box.getName().getLocalPart().startsWith("_")) {
			if (styleAttr == null) {
				if (!(box instanceof Box.BlockBox && !renderingWillStartNewBlock))
					skippedStartElement = true;
				else if (!box.hasText()) {
					boolean childrenWillRender = false;
					for (Box b : box)
						if (willRender(b, box.props, preserveStyle)) {
							childrenWillRender = true;
							break;
						}
					if (!childrenWillRender)
						skippedStartElement = true;
				}
			}
			if (!skippedStartElement) {
				QName name = box.getName();
				XMLStreamWriterHelper.writeStartElement(
					writer,
					name != null ? new QName(name.getNamespaceURI(),
					                         name.getLocalPart().substring(1),
					                         name.getPrefix())
					             : box instanceof Box.BlockBox ? DIV : SPAN);
				if (box instanceof Box.BlockBox)
					renderingWillStartNewBlock = true;
			}
		} else {
			XMLStreamWriterHelper.writeStartElement(writer, box.getName());
			for (Map.Entry<QName,String> a : box.getAttributes().entrySet())
				if (!STYLE.equals(a.getKey()) && !CLASS.equals(a.getKey()))
					XMLStreamWriterHelper.writeAttribute(writer, a);
		}
		if (styleAttr != null)
			XMLStreamWriterHelper.writeAttribute(writer, STYLE, styleAttr);
		if (box instanceof Box.InlineBox) {
			String text = ((Box.InlineBox)box).text();
			if (text != null)
				writer.writeCharacters(text);
		}
		for (Box b : box)
			renderingWillStartNewBlock = render(writer, b, box.props, renderingWillStartNewBlock, preserveStyle);
		if (!skippedStartElement) {
			writer.writeEndElement();
			if (box instanceof Box.BlockBox)
				renderingWillStartNewBlock = true;
		}
		if (box instanceof Box.InlineBox)
			renderingWillStartNewBlock = false;
		return renderingWillStartNewBlock;
	}

	private static boolean willRender(Box box, BoxProperties parentBox, boolean preserveStyle) throws XMLStreamException {
		if (!box.isAnonymous() && !box.getName().getLocalPart().startsWith("_"))
			return true;
		if (box.hasText())
			return true;
		if (preserveStyle)
			if (serializeCascadedProperties(((BoxPropertiesImpl)box.props()).relativize((BoxPropertiesImpl)parentBox)) != null)
				return true;
		for (Box b : box)
			if (willRender(b, box.props, preserveStyle))
				return true;
		return false;
	}

	private static void render(XMLStreamWriter writer, Node node) throws XMLStreamException {
		if (node instanceof Element)
			render(writer, (Element)node);
		else
			render(writer, (Text)node);
	}

	private static void render(XMLStreamWriter writer, Element element) throws XMLStreamException {
		XMLStreamWriterHelper.writeStartElement(writer, element.getName());
		XMLStreamWriterHelper.writeAttributes(writer, element.getAttributes());
		for (Node n : element.children())
			render(writer, n);
		writer.writeEndElement();
	}

	private static void render(XMLStreamWriter writer, Text text) throws XMLStreamException {
		writer.writeCharacters(text.characters());
	}

	private static String serializeCascadedProperties(Style style) {
		if (style == null || style.cascaded == null)
			return null;
		String s = Joiner.on("; ").join(ImmutableSortedSet.orderedBy(propertySorter)
		                                                  .addAll(style.cascaded.values())
		                                                  .build());
		return !s.isEmpty() ? s : null;
	}

	private static Comparator<Style.Property<?>> propertySorter = new Comparator<Style.Property<?>>() {
		public int compare(Style.Property<?> a, Style.Property<?> b) {
			return a.name.toString().compareTo(b.name);
		}
	};
}
