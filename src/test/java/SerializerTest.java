import java.io.IOException;
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
}
