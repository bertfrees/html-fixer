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
	private static final QName A = new QName(HTML_NS, "a");
	private static final QName HREF = new QName("href");
	private static final QName NAV = new QName(HTML_NS, "nav");

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
		walker = transformNavList(walker, 1, 150);
		walker = wrapList(walker, 0, 151, 1, NAV);
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
		// find ancestor that contains the specified number of blocks, or create it
		doc = wrapIfNeeded(doc, firstBlockIdx, blockCount);
		// rename to heading
		doc.renameCurrent(headingElement);
		// remove all strong within the heading
		// note that this could be done in a separate fix, but it would impose an order in which the fixes need to be applied
		// both are related enough do perform in a single fix
		BoxTreeWalker h = doc.subTree();
		h = unwrapAll(h, b -> STRONG.equals(b.getName()));
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
		// find ancestor that contains the specified number of blocks, or create it
		doc = wrapIfNeeded(doc, firstBlockIdx, blockCount);
		// rename to list or wrap with new list element
		if (doc.current().isBlockAndHasNoBlockChildren())
			doc.wrapCurrent(listElement);
		else {
			doc.renameCurrent(listElement);
			doc.firstChild();
		}
		// rename list items
		do {
			doc.renameCurrent(LI);
		} while (doc.nextSibling().isPresent());
		return doc;
	}

	/*
	 * Transform a list so that it conforms to the navigation document spec
	 * http://idpf.org/epub/301/spec/epub-contentdocs.html#sec-xhtml-nav
	 *
	 * - main list and nested lists must be "ol"
	 * - every "li" must contain either a "a" or a "span", optionally followed by a nested "ol"
	 *   (mandatory after "span")
	 */
	private static BoxTreeWalker transformNavList(BoxTreeWalker doc,
	                                              int firstBlockIdx,
	                                              int blockCount) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, firstBlockIdx);
		// find root list element
		int listBlockCount = 1;
		while (true) {
			BoxTreeWalker tmp = doc.clone();
			if (!tmp.previousSibling().isPresent()
			    && tmp.parent().isPresent()
			    && (listBlockCount = count(tmp, Box::isBlockAndHasNoBlockChildren)) <= blockCount) {
				doc = tmp;
				if (listBlockCount == blockCount && OL.equals(doc.current().getName()))
					break;
			} else
				assertThat(false);
		}
		// process items
		assertThat(doc.firstChild().isPresent());
		do {
			assertThat(LI.equals(doc.current().getName()));
			assertThat(doc.firstChild().isPresent());
			// wrap content in a
			if (!A.equals(doc.current().getName()) || doc.nextSibling().isPresent()) {
				doc.parent();
				BoxTreeWalker li = doc.subTree();
				// take first descendant a and move it
				assertThat(li.firstDescendant(b -> A.equals(b.getName())).isPresent());
				Box a = li.current();
				String href = a.getAttributes().get(HREF);
				assertThat(href != null);
				// remove this a and all other a with the same href
				li = unwrapAll(li, b -> A.equals(b.getName()) && href.equals(b.getAttributes().get(HREF)));
				li.root();
				li.wrapChildren(a.getName(), a.getAttributes());
				// remove all div within the heading
				li.root();
				Predicate<Box> isDiv = b -> DIV.equals(b.getName());
				while (li.firstDescendant(isDiv).isPresent() || li.firstFollowing(isDiv).isPresent())
					li.renameCurrent(_SPAN); // if possible unwrap at the rendering stage or otherwise rename to span
			} else
				doc.parent();
		} while (doc.nextSibling().isPresent());
		return doc;
	}

	/*
	 * Wrap a list together with some pre-content
	 *
	 * @param preContentBlockCount may be 0
	 */
	private static BoxTreeWalker wrapList(BoxTreeWalker doc,
	                                      int firstBlockIdx,
	                                      int blockCount,
	                                      int preContentBlockCount,
	                                      QName wrapper) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, firstBlockIdx + preContentBlockCount);
		// find list element
		int listBlockCount = 1;
		while (true) {
			BoxTreeWalker tmp = doc.clone();
			if (!tmp.previousSibling().isPresent()
			    && tmp.parent().isPresent()
			    && (listBlockCount = count(tmp, Box::isBlockAndHasNoBlockChildren)) <= (blockCount - preContentBlockCount)) {
				doc = tmp;
				if (listBlockCount == (blockCount - preContentBlockCount)
				    && (OL.equals(doc.current().getName()) || UL.equals(doc.current().getName())))
					break;
			} else
				assertThat(false);
		}
		boolean listHasNextSibling = doc.nextSibling().isPresent();
		if (listHasNextSibling)
			doc.previousSibling();
		// find elements belonging to pre-content
		int childrenCount = 1;
		if (preContentBlockCount > 0) {
			while (doc.previousSibling().isPresent()) {
				childrenCount++;
				preContentBlockCount -= count(doc, Box::isBlockAndHasNoBlockChildren);
				if (preContentBlockCount <= 0) break;
			}
			assertThat(preContentBlockCount == 0);
		}
		boolean preContentHasPreviousSibling = doc.previousSibling().isPresent();
		if (preContentHasPreviousSibling)
			doc.wrapNextSiblings(childrenCount, wrapper);
		else if (listHasNextSibling) {
			doc.parent();
			doc.wrapFirstChildren(childrenCount, wrapper);
		} else {
			assertThat(doc.parent().isPresent());
			if (!wrapper.equals(doc.current().getName()))
				if (DIV.equals(doc.current().getName()))
					doc.renameCurrent(wrapper);
				else
					doc.wrapChildren(wrapper);
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

	private static BoxTreeWalker unwrapAll(BoxTreeWalker doc, Predicate<Box> select) {
		doc.root();
		while (true) {
			Box current = doc.current();
			if (select.test(current))
				if (doc.firstChild().isPresent()) {
					doc.unwrapParent();
					continue;
				} else if (doc.previousSibling().isPresent()) {
					doc.unwrapNextSibling();
					if (doc.firstFollowing().isPresent())
						continue;
					else
						break;
				} else if (doc.parent().isPresent())
					doc.unwrapFirstChild();
				else
					break;
			if (doc.firstChild().isPresent() || doc.firstFollowing().isPresent())
				continue;
			else
				break;
		}
		return doc;
	}

	/* Manipulate the tree so that on return current box contains exactly the specified blocks. If
	 * needed, insert a new anonymous box. */
	private static BoxTreeWalker wrapIfNeeded(BoxTreeWalker doc,
	                                          int firstBlockIdx,
	                                          int blockCount) throws CanNotPerformTransformationException {
		doc.root();
		nthBlock(doc, firstBlockIdx);
		// find box that contains exactly the specified blocks, or if it doesn't exist find the first child box
		int firstBoxBlockCount = 1;
		while (true) {
			BoxTreeWalker tmp = doc.clone();
			if (tmp.previousSibling().isPresent())
				break;
			if (tmp.parent().isPresent()) {
				int k = count(tmp, Box::isBlockAndHasNoBlockChildren);
				if (k <= blockCount) {
					doc = tmp;
					firstBoxBlockCount = k;
				} else
					break;
			} else
					break;
		}
		if (blockCount == firstBoxBlockCount)
			return doc;
		blockCount -= firstBoxBlockCount;
		// find other child boxes
		int boxCount = 1;
		while (blockCount > 0) {
			assertThat(doc.nextSibling().isPresent());
			blockCount -= count(doc, Box::isBlockAndHasNoBlockChildren);
			boxCount++;
		}
		assertThat(blockCount == 0);
		for (int k = boxCount; k > 1; k--) doc.previousSibling();
		// wrap inside anonymous box
		if (doc.previousSibling().isPresent()) {
			doc.wrapNextSiblings(boxCount, null);
			doc.nextSibling();
		} else {
			doc.parent();
			doc.wrapFirstChildren(boxCount, null);
			doc.firstChild();
		}
		return doc;
	}

	private static void assertThat(boolean test) throws CanNotPerformTransformationException {
		if (!test) throw new CanNotPerformTransformationException();
	}

	@SuppressWarnings("serial")
	private static class CanNotPerformTransformationException extends Exception {}
}
