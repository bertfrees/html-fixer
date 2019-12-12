import java.io.IOException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Test;

public class TransformationsTest {

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
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		walker = Transformations.transformTable(walker, 0, 3, true);
		walker = Transformations.markupHeading(walker, 0, 3, H1);
		walker = Transformations.removeImage(walker, 1, 0);
		utils.serialize(walker.root());
		utils.render(walker.root(), true);
		utils.render(walker.root(), false);
	}

	@Test
	public void testTransformTheCodfishDreamContents()
		throws XMLStreamException, IOException, SaxonApiException, InterruptedException,
		       CanNotPerformTransformationException {

		URL html = BoxTreeWalkerTest.class.getResource("test2.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		walker = Transformations.transformTable(walker, 1, 150, false);
		walker = Transformations.convertToList(walker, 1, 150, OL, null, LI);
		walker = Transformations.transformNavList(walker, 1, 150);
		walker = Transformations.wrapList(walker, 0, 151, 1, NAV);
		utils.render(walker.root(), false);
	}
}
