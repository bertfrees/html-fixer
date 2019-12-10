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
	private static final QName LI = new QName(HTML_NS, "li");
	private static final QName UL = new QName(HTML_NS, "ul");
	private static final QName OL = new QName(HTML_NS, "ol");

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
		walker = transformTable(walker, 0, 3, true);
		walker = markupHeading(walker, 0, 3, H1);
		walker = removeImage(walker, 1, 0);
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
		walker = transformTable(walker, 1, 150, false);
		walker = convertToList(walker, 1, 150, OL);
		utils.render(walker.root(), false);
	}

	/*
	 * @param firstBlockIdx 0-based index of first block
	 * @param blockCount number of blocks in table row (must be at least one)
	 */
	private static BoxTreeWalker transformTable(BoxTreeWalker doc,
	                                            int firstBlockIdx,
	                                            int blockCount,
	                                            boolean singleRow) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, firstBlockIdx);
		// find and rename first cell
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
		// check that this is the first cell in the row
		assertThat(!doc.previousSibling().isPresent());
		//  rename other cells in this row
		while (doc.nextSibling().isPresent()) {
			assertThat(doc.current().props.display().equals("table-cell"));
			doc.renameCurrent(DIV);
		}
		// rename row
		assertThat(doc.parent().isPresent());
		assertThat(doc.current().props.display().equals("table-row"));
		doc.renameCurrent(DIV);
		// check that this is the first row in the table (or tbody)
		assertThat(!doc.previousSibling().isPresent());
		if (singleRow)
			assertThat(!doc.nextSibling().isPresent());
		else
			// process other rows
			while (doc.nextSibling().isPresent()) {
				assertThat(doc.current().props.display().equals("table-row"));
				doc.renameCurrent(DIV);
				assertThat(doc.firstChild().isPresent());
				doc.renameCurrent(DIV);
				while (doc.nextSibling().isPresent()) {
					assertThat(doc.current().props.display().equals("table-cell"));
					doc.renameCurrent(DIV);
				}
				doc.parent();
			}
		assertThat(doc.parent().isPresent());
		// find table
		if (doc.current().props.display().equals("table-row-group")) {
			// check that there is only one tbody and no thead or tfoot
			assertThat(!doc.nextSibling().isPresent());
			assertThat(!doc.previousSibling().isPresent());
			assertThat(doc.parent().isPresent());
		}
		assertThat(doc.current().props.display().equals("table"));
		// check number of cells in table
		assertThat(count(doc, Box::isBlockAndHasNoBlockChildren) == blockCount);
		// unwrap table
		doc.firstChild();
		doc.unwrapParent();
		return doc;
	}

	private static BoxTreeWalker markupHeading(BoxTreeWalker doc,
	                                           int firstBlockIdx,
	                                           int blockCount,
	                                           QName headingElement) throws CanNotPerformTransformationException {
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
		doc.renameCurrent(headingElement);
		// remove all strong within the heading
		// note that this could be done in a separate fix, but it would impose an order in which the fixes need to be applied
		// both are related enough do perform in a single fix
		BoxTreeWalker h = doc.subTree();
		Predicate<Box> isStrong = b -> STRONG.equals(b.getName());
		while (h.firstDescendant(isStrong).isPresent() || h.firstFollowing(isStrong).isPresent())
			if (h.previousSibling().isPresent())
				h.unwrapNextSibling();
			else if (h.parent().isPresent())
				h.unwrapFirstChild();
			else
				throw new RuntimeException("coding error");
		// remove all div within the heading
		h.root();
		Predicate<Box> isDiv = b -> DIV.equals(b.getName());
		while (h.firstDescendant(isDiv).isPresent() || h.firstFollowing(isDiv).isPresent())
			h.renameCurrent(_SPAN); // if possible unwrap at the rendering stage or otherwise rename to span
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

	private static BoxTreeWalker convertToList(BoxTreeWalker doc,
	                                           int firstBlockIdx,
	                                           int blockCount,
	                                           QName listElement) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, firstBlockIdx);
		// find first list item
		int itemBlockCount = 1;
		boolean firstItemHasPreviousSibling = false;
		while (true) {
			BoxTreeWalker tmp = doc.clone();
			int k;
			if (tmp.previousSibling().isPresent()) {
				firstItemHasPreviousSibling = true;
				break;
			}
			if (tmp.parent().isPresent() && (k = count(tmp, Box::isBlockAndHasNoBlockChildren)) < blockCount) {
				doc = tmp;
				itemBlockCount = k;
			} else
				break;
		}
		blockCount -= itemBlockCount;
		// rename list item
		doc.renameCurrent(LI);
		// rename other list items
		int itemCount = 1;
		while (blockCount > 0) {
			assertThat(doc.nextSibling().isPresent());
			doc.renameCurrent(LI);
			blockCount -= count(doc, Box::isBlockAndHasNoBlockChildren);
			itemCount++;
		}
		assertThat(blockCount == 0);
		boolean lastItemHasNextSibling = doc.nextSibling().isPresent();
		// rename parent element to ul, or wrap inside new ul element if parent element contains more than the list items only
		if (!firstItemHasPreviousSibling && !lastItemHasNextSibling) {
			doc.parent();
			doc.renameCurrent(listElement);
		} else {
			if (lastItemHasNextSibling) doc.previousSibling();
			for (int k = itemCount; k > 1; k--) doc.previousSibling();
			if (doc.previousSibling().isPresent())
				doc.wrapNextSiblings(itemCount, listElement);
			else {
				doc.parent();
				doc.wrapFirstChildren(itemCount, listElement);
			}
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
