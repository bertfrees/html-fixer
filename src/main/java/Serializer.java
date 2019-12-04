import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.daisy.common.stax.XMLStreamWriterHelper;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

public class Serializer {

	private static final QName BLOCK_BOX = new QName("BlockBox");
	private static final QName ANONYMOUS_BLOCK_BOX = new QName("AnonymousBlockBox");
	private static final QName BLOCK_CONTENT = new QName("BlockContent");
	private static final QName INLINE_BOX = new QName("InlineBox");
	private static final QName ANONYMOUS_INLINE_BOX = new QName("AnonymousInlineBox");
	private static final QName INLINE_CONTENT = new QName("InlineContent");

	public static void serialize(XMLStreamWriter writer, Box box) {
		try {
			serializeBox(writer, box, null);
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	public static void serialize(XMLStreamWriter writer, Element element) {
		try {
			XMLStreamWriterHelper.writeStartElement(writer, new QName("_" + element.getName().getLocalPart()));
			for (Map.Entry<QName,String> a : element.getAttributes().entrySet())
				XMLStreamWriterHelper.writeAttribute(writer, a);
			for (Node c : element.children())
				if (c instanceof Element)
					serialize(writer, (Element)c);
			writer.writeEndElement();
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
	}

	public static void serializeToJSON(Writer writer, Box box) {
		try {
			writer.append("{");
			writer.append("\"type\":").append(box instanceof Box.BlockBox ? "1" : "0");
			writer.append(",");
			writer.append("\"name\":");
			if (box.isAnonymous())
				writer.append("null");
			else
				serializeToJSON(writer, box.getName());
			writer.append(",");
			writer.append("\"attributes\":[");
			writer.append(
				Joiner.on(",").join(
					Iterables.transform(
						box.getAttributes().entrySet(),
						a -> (
							"{\"name\":"
							+ serializeToJSON(a.getKey())
							+ ",\"value\":\""
							+ escapeForJSONString(a.getValue()) + "\"}"
						))));
			writer.append("],");
			String text = box instanceof Box.InlineBox ? ((Box.InlineBox)box).text() : null;
			if (text != null)
				writer.append("\"text\":\"").append(escapeForJSONString(text)).append("\"");
			else
				writer.append("\"text\":null");
			writer.append(",");
			writer.append("\"children\":[");
			writer.append(
				Joiner.on(",").join(
					Iterables.transform(box, Serializer::serializeToJSON)));
			writer.append("]");
			writer.append(",");
			writer.append("\"props\":{");
			BoxProperties props = box.props();
			writer.append(
				Joiner.on(",").join(
					Iterables.transform(
						props.keySet,
						p -> {
							Object val = props.get(p);
							String v;
							if (val == null)
								v = "null";
							else if (val instanceof String)
								v = "\"" + escapeForJSONString(val.toString()) + "\"";
							else
								throw new RuntimeException();
							return "\"" + p + "\":" + v; })));
			writer.append("}");
			writer.append("}");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void serializeToJSON(Writer writer, QName name) {
		try {
			writer.append("{");
			writer.append("\"namespace\":\"").append(name.getNamespaceURI()).append("\",");
			writer.append("\"localPart\":\"").append(name.getLocalPart()).append("\",");
			writer.append("\"prefix\":\"").append(name.getPrefix()).append("\"");
			writer.append("}");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String escapeForJSONString(String str) {
		return str;
	}

	private static String serializeToJSON(Box box) {
		StringWriter writer = new StringWriter();
		serializeToJSON(writer, box);
		return writer.toString();
	}

	private static String serializeToJSON(QName name) {
		StringWriter writer = new StringWriter();
		serializeToJSON(writer, name);
		return writer.toString();
	}

	private static void serializeBox(XMLStreamWriter writer, Box box, Box parentBox) throws XMLStreamException {
		if (box.isAnonymous()) {
			if (box instanceof Box.BlockBox) {
				XMLStreamWriterHelper.writeStartElement(writer, ANONYMOUS_BLOCK_BOX);
				XMLStreamWriterHelper.writeStartElement(writer, BLOCK_CONTENT); // anonymous block box can not have child block box
				for (Box c : box) // block box must have child boxes
					serializeBox(writer, c, box);
				writer.writeEndElement();
				writer.writeEndElement();
			} else {
				XMLStreamWriterHelper.writeStartElement(writer, ANONYMOUS_INLINE_BOX);
				XMLStreamWriterHelper.writeStartElement(writer, INLINE_CONTENT);
				writer.writeCharacters(((Box.InlineBox)box).text()); // anonymous inline box can not have child boxes
				writer.writeEndElement();
				writer.writeEndElement();
			}
		} else {
			XMLStreamWriterHelper.writeStartElement(writer, new QName("_" + box.getName().getLocalPart()));
			for (Map.Entry<QName,String> a : box.getAttributes().entrySet())
				XMLStreamWriterHelper.writeAttribute(writer, a);
			if (box instanceof Box.BlockBox) {
				XMLStreamWriterHelper.writeStartElement(writer, BLOCK_BOX);
				for (String p : box.props().keySet)
					XMLStreamWriterHelper.writeAttribute(writer,
					                                     new QName("css", p, "css"), ""+box.props().get(p));
				XMLStreamWriterHelper.writeAttribute(writer, new QName("visible"), ""+box.isVisible(parentBox));
				Iterator<Box> children = box.iterator();
				if (children.hasNext()) {
					Box c = children.next();
					boolean isBlock = c instanceof Box.BlockBox;
					if (!isBlock)
						XMLStreamWriterHelper.writeStartElement(writer, BLOCK_CONTENT);
					while (true) {
						serializeBox(writer, c, box);
						if (!children.hasNext()) break;
						c = children.next();
					}
					if (!isBlock)
						writer.writeEndElement();
				}
				writer.writeEndElement();
			} else {
				XMLStreamWriterHelper.writeStartElement(writer, INLINE_BOX);
				XMLStreamWriterHelper.writeAttribute(writer, new QName("element"), box.getName().getLocalPart());
				String text = ((Box.InlineBox)box).text();
				if (text != null) {
					XMLStreamWriterHelper.writeStartElement(writer, INLINE_CONTENT);
					// box with text can not have child boxes
					writer.writeCharacters(text);
					writer.writeEndElement();
				} else
					for (Box c : box)
						serializeBox(writer, c, box);
				writer.writeEndElement();
			}
			writer.writeEndElement();
		}
	}
}
