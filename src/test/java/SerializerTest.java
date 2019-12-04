import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Test;

public class SerializerTest {

	@Test
	public void testSerialize() throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		URL html = SerializerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		utils.serialize(doc.root().getBox());
	}

	@Test
	public void testSerializeToJSON() throws IOException, InterruptedException {
		URL html = SerializerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		File tmpFile = File.createTempFile("html-analyzer-", ".json");
		Writer writer = new FileWriter(tmpFile);
		Serializer.serializeToJSON(writer, doc.root().getBox());
		writer.flush();
		writer.close();
		Runtime.getRuntime().exec("open -a Firefox " + tmpFile.getAbsolutePath()).waitFor();
	}
}
