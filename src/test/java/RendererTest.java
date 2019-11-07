import java.io.IOException;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Test;

public class RendererTest {

	@Test
	public void testRender() throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		URL html = RendererTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		utils.render(doc.root().getBox(), true);
	}
}
