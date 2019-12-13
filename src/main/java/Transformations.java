import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import com.google.common.collect.ImmutableMap;

public class Transformations {

	private BoxTreeWalker doc;
	private BoxTreeWalker root;
	private InputRange currentRange;

	public Transformations(Box doc) {
		this.root = new BoxTreeWalker(doc);
		this.doc = root.subTree();
		this.currentRange = null;
	}

	public Box get() {
		return root.current();
	}

	private static class InputRange {
		// 0-based index of start block
		public final int startBlockIndex;
		// if non-negative, 0-based index of the start inline unit within the start block
		public final int startInlineIndex;
		// number of blocks or inline units in the range
		public final int size;
		public InputRange(int startBlockIndex) {
			this(startBlockIndex, -1, 1);
		}
		public InputRange(int startBlockIndex, int size) {
			this(startBlockIndex, -1, size);
		}
		public InputRange(int startBlockIndex, int startInlineIndex, int size) {
			if (startBlockIndex < 0) throw new IllegalArgumentException();
			if (size < 1) throw new IllegalArgumentException();
			this.startBlockIndex = startBlockIndex;
			this.startInlineIndex = startInlineIndex;
			this.size = size;
		}
	}

	public Transformations moveTo(int startBlockIndex) {
		currentRange = new InputRange(startBlockIndex);
		return this;
	}

	public Transformations moveTo(int startBlockIndex, int size) {
		currentRange = new InputRange(startBlockIndex, size);
		return this;
	}

	public Transformations moveTo(int startBlockIndex, int startInlineIndex, int size) {
		currentRange = new InputRange(startBlockIndex, startInlineIndex, size);
		return this;
	}

	public Transformations transformTable(boolean singleRow) throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = transformTable(doc, currentRange.size, singleRow);
		return this;
	}

	public Transformations markupHeading(QName headingElement) throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = markupHeading(doc, currentRange.size, headingElement);
		return this;
	}

	public Transformations removeImage() throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = removeImage(doc, currentRange.size);
		return this;
	}

	public Transformations convertToList(QName listElement,
	                                     Map<QName,String> listAttributes,
	                                     QName listItemElement) throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = convertToList(doc, currentRange.size, listElement, listAttributes, listItemElement);
		return this;
	}

	public Transformations convertToPoem() throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = convertToPoem(doc, currentRange.size);
		return this;
	}

	/*
	 * Transform a list so that it conforms to the navigation document spec
	 * http://idpf.org/epub/301/spec/epub-contentdocs.html#sec-xhtml-nav
	 *
	 * - main list and nested lists must be "ol"
	 * - every "li" must contain either a "a" or a "span", optionally followed by a nested "ol"
	 *   (mandatory after "span")
	 */
	public Transformations transformNavList() throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = transformNavList(doc, currentRange.size);
		return this;
	}

	/*
	 * Wrap a list together with some pre-content
	 *
	 * @param preContentBlockCount may be 0
	 *
	 * On return current box is the wrapper
	 */
	public Transformations wrapList(int preContentBlockCount,
	                                QName wrapper) throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = wrapList(doc, currentRange.size, preContentBlockCount, wrapper);
		return this;
	}

	public Transformations wrapListInPrevious() throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = wrapListInPrevious(doc, currentRange.size);
		return this;
	}

	/*
	 * @param captionBlockCount may be 0
	 */
	public Transformations wrapInFigure(int captionBlockCount,
	                                    boolean captionBefore) throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = wrapInFigure(doc, currentRange.size, captionBlockCount, captionBefore);
		return this;
	}

	public Transformations removeHiddenBox() throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = removeHiddenBox(doc, currentRange.size);
		return this;
	}

	public Transformations markupPageBreak() throws CanNotPerformTransformationException {
		doc = moveToRange(doc, currentRange);
		doc = markupPageBreak(doc, currentRange.size);
		return this;
	}

	/////////////////////////////////////////////////////////////////////

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final String EPUB_NS = "http://www.idpf.org/2007/ops";

	private static final QName DIV = new QName(HTML_NS, "div");
	private static final QName P = new QName(HTML_NS, "p");
	private static final QName SPAN = new QName(HTML_NS, "span");
	private static final QName STRONG = new QName(HTML_NS, "strong");
	private static final QName EM = new QName(HTML_NS, "em");
	private static final QName SMALL = new QName(HTML_NS, "small");
	private static final QName IMG = new QName(HTML_NS, "img");
	private static final QName LI = new QName(HTML_NS, "li");
	private static final QName UL = new QName(HTML_NS, "ul");
	private static final QName OL = new QName(HTML_NS, "ol");
	private static final QName A = new QName(HTML_NS, "a");
	private static final QName HREF = new QName("href");
	private static final QName FIGURE = new QName(HTML_NS, "figure");
	private static final QName FIGCAPTION = new QName(HTML_NS, "figcaption");

	private static final Map<QName,String> EPUB_TYPE_Z3998_POEM = ImmutableMap.of(new QName(EPUB_NS, "type"), "z3998:poem");
	private static final Map<QName,String> EPUB_TYPE_PAGEBREAK = ImmutableMap.of(new QName(EPUB_NS, "type"), "pagebreak");

	/*
	 * @param blockCount number of blocks in table (one or more)
	 */
	private static BoxTreeWalker transformTable(BoxTreeWalker doc,
	                                            int blockCount,
	                                            boolean singleRow) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
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
	                                           int blockCount,
	                                           QName headingElement) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
		// find ancestor that contains the specified number of blocks, or create it
		doc = wrapIfNeeded(doc, blockCount);
		// rename to heading
		doc.renameCurrent(headingElement);
		// remove strong, em and small within the heading
		doc = removeEmInAllEmBox(doc, STRONG);
		doc = removeEmInAllEmBox(doc, EM);
		doc = removeEmInAllEmBox(doc, SMALL);
		// remove all div and p within the heading
		// remove all span within the heading
		BoxTreeWalker h = doc.subTree();
		Predicate<Box> isDivOrPOrSpan = b -> DIV.equals(b.getName()) || P.equals(b.getName()) || SPAN.equals(b.getName());
		while (h.firstDescendant(isDivOrPOrSpan).isPresent() || h.firstFollowing(isDivOrPOrSpan).isPresent()) {
			if (!SPAN.equals(h.current().getName()))
				h.renameCurrent(SPAN);
			h.markCurrentForUnwrap();
		}
		return doc;
	}

	private static BoxTreeWalker removeImage(BoxTreeWalker doc, int size) throws CanNotPerformTransformationException {
		assertThat(size == 1);
		assertThat(IMG.equals(doc.current().getName()));
		assertThat(doc.current().isReplacedElement());
		doc.markCurrentForRemoval();
		// also remove parent elements that have no other content than the img
		while (!doc.previousSibling().isPresent()
		       && !doc.nextSibling().isPresent()
		       && doc.parent().isPresent())
			doc.markCurrentForRemoval();
		return doc;
	}

	private static BoxTreeWalker convertToList(BoxTreeWalker doc,
	                                           int blockCount,
	                                           QName listElement,
	                                           Map<QName,String> listAttributes,
	                                           QName listItemElement) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
		// find ancestor that contains the specified number of blocks, or create it
		doc = wrapIfNeeded(doc, blockCount);
		// rename to list or wrap with new list element
		if (doc.current().isBlockAndHasNoBlockChildren())
			doc.wrapCurrent(listElement, listAttributes);
		else {
			doc.renameCurrent(listElement, listAttributes);
			doc.firstChild();
		}
		// rename list items
		do {
			doc.renameCurrent(listItemElement);
		} while (doc.nextSibling().isPresent());
		return doc;
	}

	private static BoxTreeWalker convertToPoem(BoxTreeWalker doc,
	                                           int blockCount) throws CanNotPerformTransformationException {
		return convertToList(doc, blockCount, DIV, EPUB_TYPE_Z3998_POEM, P);
	}

	private static BoxTreeWalker transformNavList(BoxTreeWalker doc,
	                                              int blockCount) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
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
		new Function<BoxTreeWalker,BoxTreeWalker>() {
			public BoxTreeWalker apply(BoxTreeWalker ol) {
				assertThat(ol.firstChild().isPresent());
				do {
					assertThat(LI.equals(ol.current().getName()));
					assertThat(ol.firstChild().isPresent());
					int childCount = 1;
					while (ol.nextSibling().isPresent()) childCount++;
					if (childCount == 1 && A.equals(ol.current().getName()) && ol.current().getAttributes().containsKey(HREF)) {
						ol.parent();
						continue;
					}
					if (OL.equals(ol.current().getName())) {
						if (childCount == 1)
							assert(false);
						// process nested list
						ol = apply(ol);
						if (childCount == 2) {
							ol.previousSibling();
							if (A.equals(ol.current().getName()) && ol.current().getAttributes().containsKey(HREF)) {
								ol.parent();
								continue;
							}
						} else {
							ol.parent();
							ol.wrapFirstChildren(childCount - 1, SPAN);
							childCount = 2;
							ol.firstChild();
						}
					} else if (childCount > 1) {
						ol.parent();
						ol.wrapChildren(SPAN);
						childCount = 1;
						ol.firstChild();
					}
					// find first descendant a
					BoxTreeWalker span = ol.subTree();
					if (!span.firstDescendant(x -> A.equals(x.getName()) && x.getAttributes().containsKey(HREF)).isPresent())
						if (childCount == 2) {
							ol.parent();
							continue;
						} else
							assertThat(false);
					Box a = span.current();
					String href = a.getAttributes().get(HREF);
					// remove this a and all other a with the same href
					span.root();
					span = unwrapAll(span, x -> A.equals(x.getName()) && href.equals(x.getAttributes().get(HREF)));
					// rename span to a
					span.renameCurrent(a.getName(), a.getAttributes());
					ol.parent();
				} while (ol.nextSibling().isPresent());
				ol.parent();
				return ol;
			}
		}.apply(doc);
		// remove all div and p within the table of contents
		BoxTreeWalker toc = doc.subTree();
		Predicate<Box> isDivOrP = x -> DIV.equals(x.getName()) || P.equals(x.getName());
		while (toc.firstDescendant(isDivOrP).isPresent() || toc.firstFollowing(isDivOrP).isPresent()) {
			toc.renameCurrent(SPAN);
			toc.markCurrentForUnwrap();
		}
		return doc;
	}

	private static BoxTreeWalker wrapList(BoxTreeWalker doc,
	                                      int blockCount,
	                                      int preContentBlockCount,
	                                      QName wrapper) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
		doc = moveNBlocks(doc, preContentBlockCount);
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
		if (preContentHasPreviousSibling) {
			doc.wrapNextSiblings(childrenCount, wrapper);
			doc.nextSibling();
		} else if (listHasNextSibling) {
			doc.parent();
			doc.wrapFirstChildren(childrenCount, wrapper);
			doc.firstChild();
		} else {
			assertThat(doc.parent().isPresent());
			if (!wrapper.equals(doc.current().getName()))
				if (DIV.equals(doc.current().getName()))
					doc.renameCurrent(wrapper);
				else {
					doc.wrapChildren(wrapper);
					doc.firstChild();
				}
		}
		return doc;
	}

	private static BoxTreeWalker wrapListInPrevious(BoxTreeWalker doc,
	                                                int blockCount) throws CanNotPerformTransformationException {
		doc = wrapList(doc, blockCount, 1, new QName("_"));
		QName wrapper = doc.firstChild().get().getName();
		doc.renameCurrent(null);
		doc.parent();
		doc.renameCurrent(wrapper);
		return doc;
	}

	private static BoxTreeWalker wrapInFigure(BoxTreeWalker doc,
	                                          int blockCount,
	                                          int captionBlockCount,
	                                          boolean captionBefore) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
		assertThat(blockCount > captionBlockCount);
		if (captionBlockCount > 0) {
			if (!captionBefore)
				doc = moveNBlocks(doc, blockCount - captionBlockCount);
			doc = wrapIfNeeded(doc, captionBlockCount);
			doc.renameCurrent(FIGCAPTION);
			doc = removeEmInAllEmBox(doc, STRONG);
			doc = removeEmInAllEmBox(doc, EM);
			doc = removeEmInAllEmBox(doc, SMALL);
			if (!captionBefore)
				doc = moveNBlocks(doc, captionBlockCount - blockCount);
			else if (!doc.current().isBlockAndHasNoBlockChildren())
				doc.firstDescendant(Box::isBlockAndHasNoBlockChildren);
		}
		doc = wrapIfNeeded(doc, blockCount);
		doc.renameCurrent(FIGURE);
		if (blockCount - captionBlockCount == 1 && captionBlockCount != 0) {
			doc.firstChild();
			doc.nextSibling();
			if (DIV.equals(doc.current().getName()) || P.equals(doc.current().getName()))
				doc.markCurrentForUnwrap();
		}
		return doc;
	}

	private static BoxTreeWalker removeHiddenBox(BoxTreeWalker doc,
	                                             int size) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
		assertThat(size == 1);
		assertThat("hidden".equals(doc.current().props.visibility()));
		// check that all contained boxes inherit visibility
		BoxTreeWalker box = doc.subTree();
		while (box.firstChild().isPresent() || box.firstFollowing().isPresent())
			assertThat("hidden".equals(box.current().props.visibility()));
		// remove
		doc.markCurrentForRemoval();
		// also remove parent elements that have no other content than the current box and are also hidden
		while (!doc.previousSibling().isPresent()
		       && !doc.nextSibling().isPresent()
		       && doc.parent().isPresent()
		       && "hidden".equals(doc.current().props.visibility())) {
			doc.markCurrentForRemoval();
		}
		return doc;
	}

	private static BoxTreeWalker markupPageBreak(BoxTreeWalker doc,
	                                             int size) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
		assertThat(size == 1);
		doc.renameCurrent(DIV, EPUB_TYPE_PAGEBREAK);
		return doc;
	}

	/*
	 * Remove all the em from this box if all text boxes in this box are a descendant of em.
	 *
	 * Can also be used for strong, small etc. instead of em.
	 */
	private static BoxTreeWalker removeEmInAllEmBox(BoxTreeWalker doc,
	                                                QName emElement) {
		BoxTreeWalker box = doc.subTree();
		boolean allStrong = true;
		while (true) {
			if (!emElement.equals(box.current().getName())) {
				if (box.current().hasText()
				    && !WHITE_SPACE.matcher(((Box.InlineBox)box.current()).text()).matches()) {
					allStrong = false;
					break;
				}
				if (box.firstChild().isPresent())
					continue;
			}
			if (box.firstFollowing().isPresent())
				continue;
			break;
		}
		if (allStrong) {
			box.root();
			unwrapAll(box, x -> emElement.equals(x.getName()));
		}
		return doc;
	}

	private static final Pattern WHITE_SPACE = Pattern.compile("\\s*");

	private BoxTreeWalker moveToRange(BoxTreeWalker doc, InputRange range) throws CanNotPerformTransformationException {
		assertThat(range != null);
		doc.root();
		int n = range.startBlockIndex;
		if (doc.current().isBlockAndHasNoBlockChildren())
			assertThat(n == 0);
		else {
			doc = moveNBlocks(doc, n + 1);
			if (range.startInlineIndex >= 0) {
				Predicate<Box> isReplacedElementOrTextBox = b -> b.hasText() || b.isReplacedElement();
				assertThat(range.startInlineIndex < count(doc, isReplacedElementOrTextBox));
				assertThat(doc.firstDescendant(isReplacedElementOrTextBox).isPresent());
				for (int i = 0; i < range.startInlineIndex; i++)
					assertThat(doc.firstFollowing(isReplacedElementOrTextBox).isPresent());
			}
		}
		return doc;
	}

	private static BoxTreeWalker moveNBlocks(BoxTreeWalker doc, int n) throws CanNotPerformTransformationException {
		if (n > 0) {
			if (doc.firstDescendant(Box::isBlockAndHasNoBlockChildren).isPresent())
				n--;
			while (n-- > 0)
				assertThat(doc.firstFollowing(Box::isBlockAndHasNoBlockChildren).isPresent());
		} else if (n < 0) {
			if (doc.firstParent(Box::isBlockAndHasNoBlockChildren).isPresent())
				n++;
			while (n++ < 0)
				assertThat(doc.firstPreceding(Box::isBlockAndHasNoBlockChildren).isPresent());
		}
		return doc;
	}

	/*
	 * Count number of boxes within the current element (including the element itself) that pass filter
	 */
	private static int count(BoxTreeWalker tree, Predicate<Box> filter) {
		tree = tree.subTree();
		int count = 0;
		if (filter.test(tree.current())) count++;
		while (tree.firstDescendant(filter).isPresent() || tree.firstFollowing(filter).isPresent())
			count++;
		return count;
	}

	private static BoxTreeWalker unwrapAll(BoxTreeWalker doc, Predicate<Box> select) {
		BoxTreeWalker subtree = doc.subTree();
		while (true) {
			Box current = subtree.current();
			if (select.test(current))
				if (subtree.firstChild().isPresent()) {
					subtree.unwrapParent();
					continue;
				} else if (subtree.previousSibling().isPresent()) {
					subtree.unwrapNextSibling();
					if (subtree.firstFollowing().isPresent())
						continue;
					else
						break;
				} else if (subtree.parent().isPresent())
					subtree.unwrapFirstChild();
				else
					break;
			if (subtree.firstChild().isPresent() || subtree.firstFollowing().isPresent())
				continue;
			else
				break;
		}
		return doc;
	}

	/* Manipulate the tree so that on return current box contains exactly the specified blocks. If
	 * needed, insert a new anonymous box. */
	private static BoxTreeWalker wrapIfNeeded(BoxTreeWalker doc,
	                                          int blockCount) throws CanNotPerformTransformationException {
		assertThat(doc.current().isBlockAndHasNoBlockChildren());
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
}
