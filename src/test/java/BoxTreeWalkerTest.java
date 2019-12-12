import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Predicate;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Test;

public class BoxTreeWalkerTest {

	@Test
	public void testRename() throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		URL html = BoxTreeWalkerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		Optional<Box> next = walker.firstDescendant(Box::isBlockAndHasNoBlockChildren);
		while (next.isPresent()) {
			walker.renameCurrent(new QName("BLOCK"));
			next = walker.firstFollowing(Box::isBlockAndHasNoBlockChildren);
		}
		utils.serialize(walker.root());
	}

	@Test
	public void testUnwrap() throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		URL html = BoxTreeWalkerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		Predicate<Box> isStrong = b -> b.getName() != null && b.getName().getLocalPart().equals("strong");
		while (true) {
			if (walker.firstDescendant(isStrong).isPresent() || walker.firstFollowing(isStrong).isPresent()) {
				if (walker.previousSibling().isPresent())
					walker.unwrapNextSibling();
				else if (walker.parent().isPresent())
					walker.unwrapFirstChild();
			} else
				break;
		}
		utils.serialize(walker.root());
	}

	@Test(expected=IllegalArgumentException.class) // block and inline can not be siblings
	public void testIllegalUnwrap() throws XMLStreamException, IOException, SaxonApiException, InterruptedException {
		URL html = BoxTreeWalkerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		walker.firstDescendant(
			b -> b.getName() != null && b.getName().getLocalPart().equals("p"));
		if (walker.previousSibling().isPresent())
			walker.unwrapNextSibling();
		else if (walker.parent().isPresent())
			walker.unwrapFirstChild();
	}
}
