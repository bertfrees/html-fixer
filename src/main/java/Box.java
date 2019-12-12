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

	public enum Rendering {
		// render as an element with name `getName()` and attributes `getAttributes()`, or as
		// ANONYMOUS if isAnonymous() is true
		DEFAULT,
		// if possible only render the contents (if preserveStyle option allows it and block
		// structure can be preserved), or otherwise render as an element with name `getName()`, or
		// with name "span" or "div" if isAnonymous() is true
		ANONYMOUS,
		// don't render
		SKIP
	}

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final QName IMG = new QName(HTML_NS, "img");

	private final QName name;
	private final Map<QName,String> attributes;
	private final ListIterable<Box> children;
	protected final String text;
	protected final BoxPropertiesImpl props;
	protected final boolean replacedElement;
	final Rendering rendering;

	// parent is the original parent box in the original box tree
	// it is only used for determining the box properties
	// it is not used for navigating to the parent box in the current tree (which may be different
	// than the original)
	// children is a function that is only called once, with this object as argument
	private Box(QName name,
	            Map<QName,String> attributes,
	            Box parent,
	            Function<Box,Supplier<Box>> children,
	            String text,
	            Style style,
	            Rendering rendering) {
		this.name = name;
		this.attributes = attributes != null ? attributes : Collections.<QName,String>emptyMap();
		this.props = new BoxPropertiesImpl(style, parent != null ? parent.props : null);
		this.children = children != null
			? MemoizingIterator.iterable(children.apply(this))
			: noChildren;
		this.text = text;
		this.replacedElement = IMG.equals(name);
		this.rendering = rendering != null ? rendering : Rendering.DEFAULT;
	}

	// create copy of box but with different element name and attributes to be used for rendering it
	// box properties and structure are not changed
	private Box(Box box, QName newName, Map<QName,String> attributes) {
		this.props = box.props;
		this.children = box.children;
		this.text = box.text;
		this.replacedElement = box.replacedElement;
		this.rendering = box.rendering;
		this.name = newName;
		this.attributes = attributes != null ? attributes : Collections.<QName,String>emptyMap();
	}

	// create copy of box but with different children
	private Box(Box box, Supplier<Box> newChildren) {
		this.name = box.name;
		this.attributes = box.attributes;
		this.props = box.props;
		this.text = box.text;
		this.replacedElement = box.replacedElement;
		this.rendering = box.rendering;
		this.children = MemoizingIterator.iterable(newChildren);
		if (this instanceof BlockBox) {
			Boolean hasBlockChildren = null;
			Boolean prevIsAnonymous = null;
			for (Box c : children) {
				if (hasBlockChildren == null)
					hasBlockChildren = (c instanceof BlockBox);
				else if (hasBlockChildren != (c instanceof BlockBox))
					throw new IllegalArgumentException("block and inline can not be siblings");
				if (c instanceof BlockBox && c.isAnonymous() && Boolean.TRUE.equals(prevIsAnonymous))
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

	// create copy of box but with different rendering
	private Box(Box box, Rendering newRendering) {
		this.name = box.name;
		this.attributes = box.attributes;
		this.props = box.props;
		this.children = box.children;
		this.text = box.text;
		this.replacedElement = box.replacedElement;
		this.rendering = newRendering;
	}

	Box copy(QName newName, Map<QName,String> attributes) {
		return (this instanceof BlockBox) ? new BlockBox(this, newName, attributes) : new InlineBox(this, newName, attributes);
	}

	Box copy(Supplier<Box> newChildren) {
		return (this instanceof BlockBox) ? new BlockBox(this, newChildren) : new InlineBox(this, newChildren);
	}

	Box copy(Rendering newRendering) {
		if (rendering == newRendering) return this;
		return (this instanceof BlockBox) ? new BlockBox(this, newRendering) : new InlineBox(this, newRendering);
	}

	public static class BlockBox extends Box {

		BlockBox(Element element, BlockBox parent, Function<Box,Supplier<Box>> children) {
			super(element.getName(), element.getAttributes(), parent, children, null, element.style, null);
		}

		private BlockBox(Box box, QName newName, Map<QName,String> attributes) {
			super(box, newName, attributes);
		}

		private BlockBox(Box box, Supplier<Box> newChildren) {
			super(box, newChildren);
		}

		private BlockBox(Box box, Rendering newRendering) {
			super(box, newRendering);
		}

		private BlockBox(BlockBox parent, Function<Box,Supplier<Box>> children) {
			super(null, null, parent, children, null, new Style(Style.BLOCK, parent.props), null);
		}
	}

	public static class AnonymousBlockBox extends BlockBox {

		AnonymousBlockBox(BlockBox parent, Function<Box,Supplier<Box>> children) {
			super(parent, children);
		}
	}

	public static class InlineBox extends Box {

		InlineBox(Element element, Box parent, Function<Box,Supplier<Box>> children) {
			super(element.getName(), element.getAttributes(), parent, children, null, element.style, null);
		}

		InlineBox(Element element, Box parent, String text) {
			super(element.getName(), element.getAttributes(), parent, null, text, element.style, null);
		}

		private InlineBox(Box box, QName newName, Map<QName,String> attributes) {
			super(box, newName, attributes);
		}

		private InlineBox(Box box, Supplier<Box> newChildren) {
			super(box, newChildren);
		}

		private InlineBox(Box box, Rendering newRendering) {
			super(box, newRendering);
		}

		private InlineBox(Box parent, String text) {
			super(null, null, parent, null, text, new Style(Style.INLINE, parent.props), null);
		}

		private InlineBox(Box parent, Function<Box,Supplier<Box>> children) {
			super(null, null, parent, children, null, new Style(Style.INLINE, parent.props), null);
		}

		public String text() {
			return text;
		}
	}

	public static class AnonymousInlineBox extends InlineBox {

		AnonymousInlineBox(Box parent, String text) {
			super(parent, text);
		}

		AnonymousInlineBox(Box parent, Function<Box,Supplier<Box>> children) {
			super(parent, children);
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

	static final ListIterable<Box> noChildren; static {
		ListIterator<Box> empty
			= new MemoizingIterator<Box>(Collections.emptyList()) {
					public Box computeNext() {
						throw new NoSuchElementException(); }};
		noChildren = () -> empty;
	}
}
