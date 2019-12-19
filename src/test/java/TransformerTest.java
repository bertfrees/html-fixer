import java.io.IOException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Test;

public class TransformerTest {

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";

	private static final QName H1 = new QName(HTML_NS, "h1");
	private static final QName NAV = new QName(HTML_NS, "nav");
	private static final QName OL = new QName(HTML_NS, "ol");
	private static final QName LI = new QName(HTML_NS, "li");

	@Test
	public void testTransformTheCodfishDreamChapter1()
		throws XMLStreamException, IOException, SaxonApiException, InterruptedException,
		       CanNotPerformTransformationException {

		URL html = BoxTreeWalkerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		Box transformed = new Transformer(doc.root().getBox())
				.moveTo(0, 3)    .transformTable(true)
				                 .markupHeading(H1)
				.moveTo(1, 0, 1) .removeImage()
				.get();
		
		utils.serialize(transformed);
		utils.render(transformed, true);
		utils.render(transformed, false);
	}

	@Test
	public void testTransformTheCodfishDreamContents()
		throws XMLStreamException, IOException, SaxonApiException, InterruptedException,
		       CanNotPerformTransformationException {

		URL html = BoxTreeWalkerTest.class.getResource("test2.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		Box transformed = new Transformer(doc.root().getBox())
				.moveTo(0, 1)    .markupHeading(H1)
				.moveTo(0, 0, 1) .removeImage()
				.moveTo(1, 150)  .transformTable(false)
				                 .convertToList(OL, null, LI)
				                 .transformNavList()
				.moveTo(0, 151)  .wrapList(1, NAV)
				.get();
		
		utils.render(transformed, false);
	}
}
