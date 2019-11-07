import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import net.sf.saxon.event.StreamWriterToReceiver;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;

final class utils {
	
	utils() {}


	static void serialize(Box box) throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		Processor processor = new Processor(false);
		net.sf.saxon.s9api.Serializer serializer = processor.newSerializer();
		File tmpFile = File.createTempFile("html-analyzer-", ".xml");
		serializer.setOutputStream(new FileOutputStream(tmpFile));
		serializer.setCloseOnCompletion(true);
		XMLStreamWriter writer = new StreamWriterToReceiver(serializer.getReceiver(processor.getUnderlyingConfiguration()));
		writer.writeStartDocument();
		Serializer.serialize(writer, box);
		writer.writeEndDocument();
		writer.flush();
		writer.close();
		Runtime.getRuntime().exec("open -a Firefox " + tmpFile.getAbsolutePath()).waitFor();
	}

	static void render(Box box, boolean preserveStyle) throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		Processor processor = new Processor(false);
		net.sf.saxon.s9api.Serializer serializer = processor.newSerializer();
		serializer.setOutputProperty(net.sf.saxon.s9api.Serializer.Property.INDENT, "no");
		File tmpFile = File.createTempFile("html-analyzer-", ".xml");
		serializer.setOutputStream(new FileOutputStream(tmpFile));
		serializer.setCloseOnCompletion(true);
		XMLStreamWriter writer = new StreamWriterToReceiver(serializer.getReceiver(processor.getUnderlyingConfiguration()));
		writer.writeStartDocument();
		Renderer.render(writer, box, preserveStyle);
		writer.writeEndDocument();
		writer.flush();
		writer.close();
		Runtime.getRuntime().exec("open -a Firefox " + tmpFile.getAbsolutePath()).waitFor();
	}
}
