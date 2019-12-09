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
	private static final QName _SPAN = new QName(HTML_NS, "_span");
	private static final QName H1 = new QName(HTML_NS, "h1");
	private static final QName STRONG = new QName(HTML_NS, "strong");
	private static final QName IMG = new QName(HTML_NS, "img");

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
	public void testTransformTheCodfishDreamChapter1()
		throws XMLStreamException, IOException, SaxonApiException, InterruptedException,
		       CanNotPerformTransformationException {

		URL html = BoxTreeWalkerTest.class.getResource("test.xhtml");
		Document doc = Parser.parse(html.openStream(), html);
		BoxTreeWalker walker = new BoxTreeWalker(doc.root().getBox());
		walker = transformSingleRowTable(walker, 0, 3);
		walker = markupHeading(walker, 0, 3);
		walker = removeImage(walker, 1, 0);
		utils.serialize(walker.root());
		utils.render(walker.root(), true);
		utils.render(walker.root(), false);
	}

	/*
	 * @param firstBlockIdx 0-based index of first block
	 * @param blockCount number of blocks in table row (must be at least one)
	 */
	private static BoxTreeWalker transformSingleRowTable(BoxTreeWalker doc,
	                                                     int firstBlockIdx,
	                                                     int blockCount) throws CanNotPerformTransformationException {
		doc.root();
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
		return doc;
	}

	private static BoxTreeWalker markupHeading(BoxTreeWalker doc,
	                                           int firstBlockIdx,
	                                           int blockCount) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, firstBlockIdx);
		// find ancestor that contains the specified number of blocks
		while (true) {
			BoxTreeWalker tmp = doc.clone();
			if (!tmp.previousSibling().isPresent()
			    && tmp.parent().isPresent()
			    && count(tmp, Box::isBlockAndHasNoBlockChildren) <= blockCount) {
				doc = tmp;
			} else {
				assertThat(count(doc, Box::isBlockAndHasNoBlockChildren) == blockCount);
				break;
			}
		}
		doc.renameCurrent(H1);
		// remove all strong within the heading
		// note that this could be done in a separate fix, but it would impose an order in which the fixes need to be applied
		// both are related enough do perform in a single fix
		BoxTreeWalker h1 = doc.subTree();
		Predicate<Box> isStrong = b -> STRONG.equals(b.getName());
		while (h1.firstDescendant(isStrong).isPresent() || h1.firstFollowing(isStrong).isPresent())
			if (h1.previousSibling().isPresent())
				h1.unwrapNextSibling();
			else if (h1.parent().isPresent())
				h1.unwrapFirstChild();
			else
				throw new RuntimeException("coding error");
		// remove all div within the heading
		h1Walker.root();
		Predicate<Box> isDiv = b -> DIV.equals(b.getName());
		while (h1Walker.firstDescendant(isDiv).isPresent() || h1Walker.firstFollowing(isDiv).isPresent())
			h1Walker.renameCurrent(_SPAN); // if possible unwrap at the rendering stage or otherwise rename to span
		return doc;
	}

	/*
	 * @param blockIdx 0-based index of block that contains (or is) the img
	 * @param inlineIdx 0-based index of img within the block, or -1 if the block is the img
	 */
	private static BoxTreeWalker removeImage(BoxTreeWalker doc,
	                                         int blockIdx,
	                                         int inlineIdx) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, blockIdx);
		if (inlineIdx >= 0) {
			assertThat(inlineIdx < count(doc, b -> b.hasText() || b.isReplacedElement()));
			nthReplacedElementOrTextBox(doc, inlineIdx);
		}
		assertThat(IMG.equals(doc.current().getName()));
		assertThat(doc.current().isReplacedElement());
		doc.renameCurrent(_SPAN);
		// also remove parent elements that have no other content than the img
		while (!doc.previousSibling().isPresent()
		       && !doc.nextSibling().isPresent()
		       && doc.parent().isPresent()) {
			doc.renameCurrent(_SPAN);
		}
		return doc;
	}

	private static void nthBlock(BoxTreeWalker doc, int index) throws CanNotPerformTransformationException {
		assertThat(doc.firstDescendant(Box::isBlockAndHasNoBlockChildren).isPresent());
		for (int i = 0; i < index; i++)
			assertThat(doc.firstFollowing(Box::isBlockAndHasNoBlockChildren).isPresent());
	}

	private static void nthReplacedElementOrTextBox(BoxTreeWalker doc, int index) throws CanNotPerformTransformationException {
		Predicate<Box> isReplacedElementOrTextBox = b -> b.hasText() || b.isReplacedElement();
		assertThat(doc.firstDescendant(isReplacedElementOrTextBox).isPresent());
		for (int i = 0; i < index; i++)
			assertThat(doc.firstFollowing(isReplacedElementOrTextBox).isPresent());
	}

	// count number of boxes within the current element (including the element itself) that pass filter
	private static int count(BoxTreeWalker tree, Predicate<Box> filter) {
		tree = tree.subTree();
		int count = 0;
		if (filter.test(tree.current())) count++;
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
