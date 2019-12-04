import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.xml.namespace.QName;

import com.google.common.collect.ImmutableSet;

public class Box implements Iterable<Box> {

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final QName IMG = new QName(HTML_NS, "img");

	private final QName name;
	private final Map<QName,String> attributes;
	private final ListIterable<Box> children;
	protected final String text;
	protected final BoxPropertiesImpl props;
	protected final boolean replacedElement;

	// parent is the original parent box in the original box tree
	// it is only used for determining the box properties
	// it is not used for navigating to the parent box in the current tree (which may be different
	// than the original)
	// children is a function that is only called once, with this object as argument
	private Box(QName name, Map<QName,String> attributes, Box parent, Function<Box,Supplier<Box>> children, String text, Style style) {
		this.name = name;
		this.attributes = attributes != null ? attributes : Collections.<QName,String>emptyMap();
		this.props = new BoxPropertiesImpl(style, parent != null ? parent.props : null);
		this.children = children != null
			? MemoizingIterator.iterable(children.apply(this))
			: noChildren;
		this.text = text;
		this.replacedElement = IMG.equals(name);
	}

	private Box(Element element, Box parent, Function<Box,Supplier<Box>> children, String text, Style style) {
		this(element != null ? element.getName() : null,
		     element != null ? element.getAttributes() : null,
		     parent,
		     children,
		     text,
		     style);
	}

	// create copy of box but with different element name and attributes to be used for rendering it
	// box properties and structure are not changed
	private Box(Box box, Element newName) {
		this.props = box.props;
		this.children = box.children;
		this.text = box.text;
		this.replacedElement = box.replacedElement;
		this.name = newName.getName();
		this.attributes = newName.getAttributes();
	}

	private Box(Box box, QName newName) {
		this.props = box.props;
		this.children = box.children;
		this.text = box.text;
		this.replacedElement = box.replacedElement;
		this.name = newName;
		this.attributes = Collections.<QName,String>emptyMap();
	}

	// create copy of box but with different children
	private Box(Box box, Supplier<Box> newChildren) {
		this.name = box.name;
		this.attributes = box.attributes;
		this.props = box.props;
		this.text = box.text;
		this.replacedElement = box.replacedElement;
		this.children = MemoizingIterator.iterable(newChildren);
		if (this instanceof BlockBox) {
			Boolean hasBlockChildren = null;
			Boolean prevIsAnonymous = null;
			for (Box c : children) {
				if (hasBlockChildren == null)
					hasBlockChildren = (c instanceof BlockBox);
				else if (hasBlockChildren != (c instanceof BlockBox))
					throw new IllegalArgumentException("block and inline can not be siblings");
				if (c.isAnonymous() && Boolean.TRUE.equals(prevIsAnonymous))
					throw new IllegalArgumentException("no adjacent anonymous block boxes");
				prevIsAnonymous = c.isAnonymous();
			}
			isBlockAndHasNoBlockChildren = !hasBlockChildren;
		} else {
			for (Box c : children)
				if (c instanceof BlockBox)
					throw new IllegalArgumentException("no block inside inline");
		}
	}

	Box copy(QName newName) {
		return (this instanceof BlockBox) ? new BlockBox(this, newName) : new InlineBox(this, newName);
	}

	Box copy(Supplier<Box> newChildren) {
		return (this instanceof BlockBox) ? new BlockBox(this, newChildren) : new InlineBox(this, newChildren);
	}

	public static class BlockBox extends Box {

		private BlockBox(Element element, BlockBox parent, Function<Box,Supplier<Box>> children, String text, Style style) {
			super(element, parent, children, text, style);
		}

		BlockBox(Element element, BlockBox parent, Function<Box,Supplier<Box>> children) {
			super(element, parent, children, null, element.style);
		}

		private BlockBox(Box box, QName newName) {
			super(box, newName);
		}

		private BlockBox(Box box, Supplier<Box> newChildren) {
			super(box, newChildren);
		}
	}

	public static class AnonymousBlockBox extends BlockBox {

		AnonymousBlockBox(BlockBox parent, Function<Box,Supplier<Box>> children) {
			super(null, parent, children, null, new Style(Style.BLOCK, parent.props));
		}
	}

	public static class InlineBox extends Box {

		private InlineBox(Element element, Box parent, Function<Box,Supplier<Box>> children, String text, Style style) {
			super(element, parent, children, text, style);
		}

		InlineBox(Element element, Box parent, Function<Box,Supplier<Box>> children) {
			super(element, parent, children, null, element.style);
		}

		InlineBox(Element element, Box parent, String text) {
			super(element, parent, null, text, element.style);
		}

		private InlineBox(Box box, QName newName) {
			super(box, newName);
		}

		private InlineBox(Box box, Supplier<Box> newChildren) {
			super(box, newChildren);
		}

		public String text() {
			return text;
		}
	}

	public static class AnonymousInlineBox extends InlineBox {

		AnonymousInlineBox(Box parent, String text) {
			super(null, parent, null, text, new Style(Style.INLINE, parent.props));
		}
	}

	public ListIterator<Box> children() {
		return children.iterator();
	}

	public Iterator<Box> iterator() {
		return children();
	}

	public BoxProperties props() {
		return props;
	}

	private Boolean isBlockAndHasNoBlockChildren = null;
	public boolean isBlockAndHasNoBlockChildren() {
		if (isBlockAndHasNoBlockChildren == null) {
			if (this instanceof InlineBox)
				isBlockAndHasNoBlockChildren = false;
			else
				try {
					isBlockAndHasNoBlockChildren = children().next() instanceof InlineBox;
				} catch (NoSuchElementException e) {
					isBlockAndHasNoBlockChildren = true;
				}
		}
		return isBlockAndHasNoBlockChildren;
	}

	private Boolean hasText = null;
	public boolean hasText() {
		if (hasText == null) {
			if (this instanceof InlineBox)
				hasText = ((InlineBox)this).text() != null;
			else
				hasText = false;
		}
		return hasText;
	}

	public boolean isReplacedElement() {
		return replacedElement;
	}

	public boolean isAnonymous() {
		return getName() == null;
	}

	// the element name to be used for rendering this box
	public QName getName() {
		return name;
	}

	// the element name and attributes to be used for rendering this box
	// the properties and original structure of this box are determined by the element that it was
	// originally associated with
	// the current element has no influence on the box properties and structure
	public Map<QName,String> getAttributes() {
		return attributes;
	}

	private static final Set<QName> unnecessaryAttributes = ImmutableSet.of(
		new QName("style"),
		new QName("class"));

	public boolean hasNecessaryAttributes() {
		for (QName a : attributes.keySet())
			if (!unnecessaryAttributes.contains(a))
				return true;
		return false;
	}

	// whether this box could be deleted (unwrapped) while preserving the visual presentation of
	// the document
	public boolean isVisible(Box parent) {
		String bg = props.backgroundColor();
		if (bg == null)
			return false;
		if (parent == null)
			return true;
		String parentBg = ((BlockBox)parent).props.backgroundColor();
		return !bg.equals(parentBg);
	}

	private static final ListIterable<Box> noChildren; static {
		ListIterator<Box> empty
			= new MemoizingIterator<Box>(Collections.emptyList()) {
					public Box computeNext() {
						throw new NoSuchElementException(); }};
		noChildren = () -> empty;
	}
}
