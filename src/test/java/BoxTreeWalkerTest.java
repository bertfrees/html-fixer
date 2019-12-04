import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Predicate;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Test;

public class BoxTreeWalkerTest {

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final QName DIV = new QName(HTML_NS, "div");

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

	@Test
	public void testTransformSingleRowTable() throws XMLStreamException, IOException, SaxonApiException, InterruptedException,
	                                                 CanNotPerformTransformationException {
		URL html = BoxTreeWalkerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		transformSingleRowTable(walker, 0, 3);
		utils.serialize(walker.root());
		utils.render(walker.root(), true);
		utils.render(walker.root(), false);
	}

	/*
	 * @param firstBlockIdx 0-based index of first block
	 * @param blockCount number of blocks in table row (must be at least one)
	 */
	private static void transformSingleRowTable(BoxTreeWalker doc,
	                                            int firstBlockIdx,
	                                            int blockCount) throws CanNotPerformTransformationException {
		nthBlock(doc, firstBlockIdx);
		while (true) {
			assertThat(!doc.previousSibling().isPresent());
			if (doc.current().props.display().equals("table-cell"))
				break;
			else {
				assertThat(!doc.current().props.display().equals("block"));
				assertThat(doc.parent().isPresent());
			}
		}
		doc.renameCurrent(DIV);
		assertThat(!doc.previousSibling().isPresent());
		while (true) {
			if (doc.nextSibling().isPresent()) {
				assertThat(doc.current().props.display().equals("table-cell"));
				doc.renameCurrent(DIV);
			} else
				break;
		}
		assertThat(doc.parent().isPresent());
		assertThat(doc.current().props.display().equals("table-row"));
		doc.renameCurrent(DIV);
		assertThat(!doc.nextSibling().isPresent());
		assertThat(!doc.previousSibling().isPresent());
		assertThat(doc.parent().isPresent());
		if (doc.current().props.display().equals("table-row-group")) {
			assertThat(!doc.nextSibling().isPresent());
			assertThat(!doc.previousSibling().isPresent());
			assertThat(doc.parent().isPresent());
		}
		assertThat(doc.current().props.display().equals("table"));
		doc.firstChild();
		doc.unwrapParent();
		BoxTreeWalker table = doc.subTree();
		assertThat(count(table, Box::isBlockAndHasNoBlockChildren) == blockCount);
	}

	private static void nthBlock(BoxTreeWalker doc, int index) throws CanNotPerformTransformationException {
		assertThat(doc.firstDescendant(Box::isBlockAndHasNoBlockChildren).isPresent());
		for (int i = 0; i < index; i++)
			assertThat(doc.firstFollowing(Box::isBlockAndHasNoBlockChildren).isPresent());
	}

	// count number of boxes within the tree that pass filter
	private static int count(BoxTreeWalker tree, Predicate<Box> filter) {
		int count = 0;
		while (tree.firstDescendant(filter).isPresent() || tree.firstFollowing(filter).isPresent())
			count++;
		return count;
	}

	private static void assertThat(boolean test) throws CanNotPerformTransformationException {
		if (!test) throw new CanNotPerformTransformationException();
	}

	@SuppressWarnings("serial")
	private static class CanNotPerformTransformationException extends Exception {}
}
