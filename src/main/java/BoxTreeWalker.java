import java.lang.reflect.InvocationTargetException;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.xml.namespace.QName;

class BoxTreeWalker implements Cloneable {

	protected Box root;
	private Stack<ListIterator<Box>> path;
	private Box current;

	private final static Optional<Box> noSuchElement = Optional.<Box>empty();

	public BoxTreeWalker(Box root) {
		this.root = root;
		path = new Stack<>();
		current = root;
	}

	// view of the portion of the tree with the current box as the root
	public BoxTreeWalker subTree() {
		if (path.isEmpty())
			return this;
		BoxTreeWalker fullTree = this;
		return new BoxTreeWalker(fullTree.current()) {
			@Override
			protected void updateRoot(Box root) {
				if (this.root != fullTree.current())
					throw new ConcurrentModificationException();
				else {
					this.root = root;
					fullTree.updateCurrent(root);
				}
			}
		};
	}

	public Box current() {
		return current;
	}

	public Box root() {
		while (parent().isPresent());
		return current;
	}

	public Optional<Box> previousSibling() {
		if (path.empty())
			return noSuchElement;
		ListIterator<Box> siblings = path.peek();
		siblings.previous();
		if (!siblings.hasPrevious()) {
			siblings.next();
			return noSuchElement;
		}
		current = siblings.previous();
		siblings.next();
		return Optional.of(current);
	}

	public Optional<Box> nextSibling() {
		if (path.empty())
			return noSuchElement;
		ListIterator<Box> siblings = path.peek();
		if (!siblings.hasNext())
			return noSuchElement;
		current = siblings.next();
		return Optional.of(current);
	}

	public Optional<Box> parent() {
		if (path.empty())
			return noSuchElement;
		path.pop();
		if (path.empty())
			current = root;
		else {
			ListIterator<Box> peek = path.peek();
			current = peek.previous();
			peek.next();
		}
		return Optional.of(current);
	}

	public Optional<Box> firstChild() {
		ListIterator<Box> children = current.children();
		if (!children.hasNext())
			return noSuchElement;
		current = children.next();
		path.push(children);
		return Optional.of(current);
	}

	public Optional<Box> firstFollowing() {
		for (int i = path.size() - 1; i >= 0 ; i--) {
			ListIterator<Box> siblings = path.get(i);
			if (siblings.hasNext()) {
				current = siblings.next();
				path.setSize(i + 1);
				return Optional.of(current);
			}
		}
		return noSuchElement;
	}

	public Optional<Box> firstPreceding() {
		for (int i = path.size() - 1; i >= 0 ; i--) {
			ListIterator<Box> siblings = path.get(i);
			siblings.previous();
			if (siblings.hasPrevious()) {
				current = siblings.previous();
				siblings.next();
				path.setSize(i + 1);
				while (true) {
					ListIterator<Box> children = current.children();
					if (children.hasNext()) {
						while (children.hasNext()) {
							current = children.next();
							path.push(children);
						}
					} else
						break;
				}
				return Optional.of(current);
			} else
				siblings.next();
		}
		return noSuchElement;
	}

	public Optional<Box> firstParent(Predicate<Box> filter) {
		for (int i = path.size() - 2; i >= 0 ; i--) {
			Box parent = path.get(i).previous();
			path.get(i).next();
			if (filter.test(parent)) {
				path.setSize(i + 1);
				current = parent;
				return Optional.of(current);
			}
		}
		if (filter.test(root)) {
			path.setSize(0);
			current = root;
			return Optional.of(current);
		}
		return noSuchElement;
	}

	public Optional<Box> firstDescendant(Predicate<Box> filter) {
		int startDepth = path.size();
		while (true) {
			Optional<Box> next;
			if (!(next = firstChild()).isPresent())
				if (path.size() == startDepth)
					return noSuchElement;
				else if (!(next = nextSibling()).isPresent())
					while (true)
						if ((next = parent()).isPresent()) {
							if (path.size() == startDepth)
								return noSuchElement;
							if ((next = nextSibling()).isPresent())
								break;
						} else
							break;
			if (next.isPresent()) {
				if (filter.test(next.get()))
					return next;
			} else
				break;
		}
		return noSuchElement;
	}

	public Optional<Box> firstFollowing(Predicate<Box> filter) {
		Stack<ListIterator<Box>> savePath = deepCopy(path);
		Box saveCurrent = current;
		while (true) {
			Optional<Box> next;
			if ((next = firstFollowing()).isPresent()) {
				if (filter.test(next.get()) || (next = firstDescendant(filter)).isPresent())
					return next;
			} else
				break;
		}
		path = savePath;
		current = saveCurrent;
		return noSuchElement;
	}

	public Optional<Box> firstPreceding(Predicate<Box> filter) {
		Stack<ListIterator<Box>> savePath = deepCopy(path);
		Box saveCurrent = current;
		Optional<Box> previous;
		if ((previous = firstPreceding()).isPresent()) {
			if (filter.test(previous.get()))
				return previous;
			else
				while (true) {
					if ((previous = previousSibling()).isPresent()) {
						while (true)
							if ((previous = firstChild()).isPresent()) {
								if ((previous = nextSibling()).isPresent())
									while ((previous = nextSibling()).isPresent());
							} else
								break;
					} else {
						previous = parent();
						if (!previous.isPresent())
							break;
					}
					if (filter.test(previous.get()))
						return previous;
				}
		}
		path = savePath;
		current = saveCurrent;
		return noSuchElement;
	}

	// null means make anonymous
	// attributes are copied, including style, but style does not influence box properties (-> when name is Element, not QName)
	// returns current box (renamed, but properties unchanged)
	public Box renameCurrent(QName name) {
		Box renamed = current.copy(name);
		updateCurrent(renamed);
		return current;
	}

	public Box deleteFirstChild() {
		ListIterator<Box> children = current.children();
		if (!children.hasNext())
			throw new RuntimeException("there is no first child");
		children.next();
		Supplier<Box> newChildren = children::next;
		updateCurrent(current.copy(newChildren));
		return current;
	}

	// returns current box (with different structure, but properties and visual presentation unchanged)
	public Box unwrapFirstChild() {
		ListIterator<Box> children = current.children();
		if (!children.hasNext())
			throw new RuntimeException("there is no first child");
		Box firstChild = children.next();
		children.previous();
		Supplier<Box> newChildren = firstChild.hasText()
			? updateIn(children, 0, firstChild.copy((QName)null))
			: updateIn(children, 0, firstChild.children());
		updateCurrent(current.copy(newChildren));
		return current;
	}

	// returns current box (with different next sibling)
	public Box unwrapNextSibling() {
		if (path.empty())
			throw new RuntimeException("there is no next sibling");
		ListIterator<Box> siblings = path.peek();
		if (!siblings.hasNext())
			throw new RuntimeException("there is no next sibling");
		Box parent = parent().get();
		Box nextSibling = siblings.next();
		int i = rewind(siblings);
		Supplier<Box> newSiblings = nextSibling.hasText()
			? updateIn(siblings, i - 1, nextSibling.copy((QName)null))
			: updateIn(siblings, i - 1, nextSibling.children());
		updateCurrent(parent.copy(newSiblings));
		firstChild();
		while (i-- > 2) nextSibling();
		return current;
	}

	// returns current box (with different parent and siblings)
	public Box unwrapParent() {
		if (path.empty())
			throw new RuntimeException("there is no parent");
		if (path.size() == 1)
			throw new RuntimeException("root can not be unwrapped");
		ListIterator<Box> siblings = path.peek();
		parent();
		ListIterator<Box> parentSiblings = path.peek();
		int i = rewind(siblings);
		int j = rewind(parentSiblings);
		Supplier<Box> newSiblings = updateIn(parentSiblings, j - 1, siblings);
		Box newParent = parent().get();
		updateCurrent(newParent.copy(newSiblings));
		firstChild();
		while (i-- > 1) nextSibling();
		while (j-- > 1) nextSibling();
		return current;
	}

	// null means wrap in anonymous element
	// attributes are copied, including style, but actual style is ignored
	// returns current box (within new parent, but properties and structure unchanged)
	public Box wrapCurrent(Element wrapper) {
		throw new UnsupportedOperationException();
	}

	// null means wrap in anonymous element
	// attributes are copied, including style, but actual style is ignored
	// returns current box (with different structure, but properties and visual presentation unchanged)
	public Box wrapFirstChildren(int childrenCount, Element wrapper) {
		throw new UnsupportedOperationException();
	}

	// null means wrap in anonymous element
	// attributes are copied, including style, but actual style is ignored
	// returns current box (with new siblings, but properties unchanged)
	public Box wrapNextSiblings(int siblingCount, Element wrapper) {
		throw new UnsupportedOperationException();
	}

	private void updateCurrent(Box newCurrent) {
		if (path.empty())
			updateRoot(newCurrent);
		else {
			Stack<ListIterator<Box>> newPath = new Stack<>();
			Box cur = newCurrent;
			while (!path.empty()) {
				ListIterator<Box> siblings = path.peek();
				Box parent = parent().get();
				int i = rewind(siblings);
				cur = parent.copy(updateIn(siblings, i - 1, cur));
				siblings = cur.children();
				forward(siblings, i);
				newPath.add(0, siblings);
			}
			updateRoot(cur);
			path = newPath;
		}
		current = newCurrent;
	}

	// protected only so that it can be overridden by subTree()
	protected void updateRoot(Box root) {
		this.root = root;
	}

	@Override
	public BoxTreeWalker clone() {
		BoxTreeWalker clone;
		try {
			clone = (BoxTreeWalker)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError("coding error");
		}
		clone.path = deepCopy(path);
		return clone;
	}

	private static int rewind(ListIterator<Box> iterator) {
		int i = 0;
		while (iterator.hasPrevious()) {
			iterator.previous();
			i++;
		}
		return i;
	}

	private static void forward(ListIterator<Box> iterator, int toIndex) {
		while (toIndex-- > 0)
			iterator.next();
	}

	private static Supplier<Box> updateIn(Iterator<Box> children, int index, Box newChild) {
		return new Supplier<Box>() {
			int i = 0;
			public Box get() {
				if (i++ == index) {
					children.next();
					return newChild;
				} else
					return children.next();
			}
		};
	}

	private static Supplier<Box> updateIn(Iterator<Box> children, int index, Iterator<Box> newChildren) {
		return new Supplier<Box>() {
			int i = 0;
			public Box get() {
				if (i == index)
					children.next();
				if (i++ >= index && newChildren.hasNext()) {
					return newChildren.next();
				} else
					return children.next();
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static <T extends Cloneable> Stack<T> deepCopy(Stack<T> of) {
		Stack<T> copy = new Stack<>();
		for (T t : of)
			try {
				copy.push((T)t.getClass().getMethod("clone").invoke(t)); }
			catch (IllegalAccessException
			       | IllegalArgumentException
			       | InvocationTargetException
			       | NoSuchMethodException
			       | SecurityException e) {
				throw new RuntimeException("Could not invoke clone() method", e);
			}
		return copy;
	}
}
