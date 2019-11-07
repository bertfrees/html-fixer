import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.daisy.common.stax.XMLStreamWriterHelper;

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
