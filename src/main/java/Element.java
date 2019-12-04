import java.net.URI;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import cz.vutbr.web.domassign.StyleMap;

public class Element implements Node {

	private static final String HTML_NS = "http://www.w3.org/1999/xhtml";
	private static final QName IMG = new QName(HTML_NS, "img");
	private static final QName SRC = new QName("src");

	final Element parent;
	private final QName name;
	private final Map<QName,String> attributes;
	private final Iterable<Node> children;
	final Style style;
	private final BoxPropertiesImpl boxProps;

	// assumes element is immutable
	Element(Element parent, final org.w3c.dom.Element element, StyleMap style) {
		this.parent = parent;
		this.name = nodeName(element);
		final URI baseURI = URI.create(element.getBaseURI());
		this.attributes = new AbstractMap<QName,String>() {
				Set<Entry<QName,String>> entrySet = new AbstractSet<Entry<QName,String>>() {
						org.w3c.dom.NamedNodeMap attributes = null;
						int size, i = 0;
						List<Entry<QName,String>> list = new ArrayList<>();
						public final Iterator<Entry<QName,String>> iterator() {
							return new MemoizingIterator<Entry<QName,String>>(list) {
								public Entry<QName,String> computeNext() {
									if (i < size()) {
										org.w3c.dom.Node attr = attributes.item(i++);
										QName name = nodeName(attr);
										if ("http://www.w3.org/2000/xmlns/".equals(name.getNamespaceURI()))
											return computeNext();
										String value = attr.getNodeValue();
										if (IMG.equals(Element.this.name) && SRC.equals(name))
											value = baseURI.resolve(value).toString();
										return new SimpleImmutableEntry<>(name, value);
									}
									throw new NoSuchElementException();
								}
							};
						}
						public int size() {
							if (attributes == null) {
								attributes = element.getAttributes();
								size = attributes.getLength();
							}
							return size;
						}
					};
				public Set<Entry<QName,String>> entrySet() {
					return entrySet;
				}
			};
		this.children = new Iterable<Node>() {
				org.w3c.dom.NodeList children = null;
				int size, i = 0;
				List<Node> list = new ArrayList<>();
				public Iterator<Node> iterator() {
					return new MemoizingIterator<Node>(list) {
						public Node computeNext() {
							// ignore child notes of replaced elements
							if (IMG.equals(Element.this.name))
								throw new NoSuchElementException();
							if (children == null) {
								children = element.getChildNodes();
								size = children.getLength();
							}
							while (i < size) {
								org.w3c.dom.Node child = children.item(i++);
								if (child instanceof org.w3c.dom.Element)
									return new Element(Element.this, (org.w3c.dom.Element)child, style);
								else if (child instanceof org.w3c.dom.Text)
									return new Text((org.w3c.dom.Text)child);
							}
							throw new NoSuchElementException();
						}
					};
				}
			};
		this.style = new Style(style.get(element), parent != null ? parent.style : null);
		this.boxProps = new BoxPropertiesImpl(this.style, parent != null ? parent.boxProps : null);
	}

	public QName getName() {
		return name;
	}

	public Map<QName,String> getAttributes() {
		return attributes;
	}

	public Iterable<Node> children() {
		return children;
	}

	// returns null if the display property of this element or one of its ancestors is "none",
	// "table-column-group" or "table-column"
	// note that the CSS box module allows elements of type inline to contain elements of type block
	// in which case it will not map to a box either, but this is not supported in this model
	private Box box = null;
	public Box getBox() {
		if (getComputedDisplay().equals("none")
		    || getComputedDisplay().equals("table-column-group")
		    || getComputedDisplay().equals("table-column"))
			return null;
		if (box == null) {
			Box parentBox = parent == null ? null : parent.getBox();
			boolean isBlock = !getComputedDisplay().equals("inline");
			if (isBlock && parentBox instanceof Box.InlineBox)
				throw new RuntimeException();
			if (!isBlock
			    && !Iterables.any(children,
			                      n -> n instanceof Element && !(((Element)n).getComputedDisplay().equals("none")
			                                                     || ((Element)n).getComputedDisplay().equals("table-column-group")
			                                                     || ((Element)n).getComputedDisplay().equals("table-column"))))
				box = new Box.InlineBox(this, parentBox, stringValue(children));
			else {
				Function<Box,Supplier<Box>> childBoxes = thisBox ->
					new Supplier<Box>() {
						Boolean hasBlocks = null;
						Iterator<Node> processElements = null;
						Iterator<List<Node>> blockGroups = null;
						Iterator<List<Node>> inlineGroups = null;
						public Box get() {
							if (processElements != null) {
								while (processElements.hasNext()) {
									Box b = ((Element)processElements.next()).getBox();
									if (b != null) return b;
								}
								processElements = null;
							}
							if (inlineGroups != null && inlineGroups.hasNext()) {
								List<Node> g = inlineGroups.next();
								if (g.get(0) instanceof Text) {
									String text = stringValue(g);
									if (!isWhiteSpaceOnly(text))
										return new Box.AnonymousInlineBox(thisBox, text);
								} else {
									processElements = g.iterator();
									return get();
								}
							}
							if (blockGroups == null)
								blockGroups = groupAdjacent(children, Element::isBlock).iterator();
							while (blockGroups.hasNext()) {
								List<Node> g = blockGroups.next();
								if (isBlock(g.get(0))) {
									processElements = g.iterator();
									return get();
								} else {
									if (hasBlocks == null)
										hasBlocks = Iterables.any(children, Element::isBlock);
									if (hasBlocks) {
										if (Iterables.any(g, Element::isInline) || !isWhiteSpaceOnly(stringValue(g)))
											return new Box.AnonymousBlockBox(
												(Box.BlockBox)thisBox,
												b -> {
													List<Box> inlineBoxes = new ArrayList<>();
													for (List<Node> gg : groupAdjacent(g, Predicates.instanceOf(Text.class))) {
														if (gg.get(0) instanceof Text) {
															String text = stringValue(gg);
															if (!isWhiteSpaceOnly(text))
																inlineBoxes.add(new Box.AnonymousInlineBox(b, text));
														} else
															for (Node n : gg) {
																Box inlineBox = ((Element)n).getBox();
																if (inlineBox != null) inlineBoxes.add(inlineBox);
															}
													}
													return inlineBoxes.iterator()::next;
												});
									} else {
										inlineGroups = groupAdjacent(g, Predicates.instanceOf(Text.class)).iterator();
										return get();
									}
								}
							}
							throw new NoSuchElementException();
						}
					};
				if (isBlock)
					box = new Box.BlockBox(this, (Box.BlockBox)parentBox, childBoxes);
				else
					box = new Box.InlineBox(this, parentBox, childBoxes);
			}
		}
		return box;
	}

	private String getComputedDisplay() {
		return boxProps.display();
	}

	private static QName nodeName(org.w3c.dom.Node node) {
		String prefix = node.getPrefix();
		String ns = node.getNamespaceURI();
		String localPart = node.getLocalName();
		if (prefix != null)
			return new QName(ns, localPart, prefix);
		else
			return new QName(ns, localPart);
	}

	private static List<List<Node>> groupAdjacent(Iterable<Node> nodes, Predicate<Object> key) {
		List<List<Node>> list = new ArrayList<>();
		List<Node> adjacent = new ArrayList<>();
		boolean currentKey = false;
		for (Node n : nodes) {
			boolean nextKey = key.apply(n);
			if (currentKey != nextKey && !adjacent.isEmpty()) {
				list.add(adjacent);
				adjacent = new ArrayList<>();
			}
			adjacent.add(n);
			currentKey = nextKey;
		}
		if (!adjacent.isEmpty())
			list.add(adjacent);
		return list;
	}

	private static boolean isBlock(Object n) {
		return n instanceof Element
			&& ((Element)n).getBox() instanceof Box.BlockBox;
	}

	private static boolean isInline(Object n) {
		return n instanceof Element
			&& ((Element)n).getBox() instanceof Box.InlineBox;
	}

	private static String stringValue(Iterable<Node> nodes) {
		StringBuilder s = new StringBuilder();
		for (Node n : nodes) {
			if (n instanceof Text)
				s.append(((Text)n).characters());
			else {
				Element e = (Element)n;
				if (!(e.getComputedDisplay().equals("none")
				      || e.getComputedDisplay().equals("table-column-group")
				      || e.getComputedDisplay().equals("table-column")))
					s.append(stringValue(e.children()));
			}
		}
		return s.toString();
	}

	private static final Pattern WHITE_SPACE = Pattern.compile("\\s*");

	private static boolean isWhiteSpaceOnly(String text) {
		return WHITE_SPACE.matcher(text).matches();
	}
}
