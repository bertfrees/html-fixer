public class Text implements Node {

	private final String text;

	Text(org.w3c.dom.Text node) {
		text = node.getNodeValue();
	}

	public String characters() {
		return text;
	}

	@Override
	public String toString() {
		return text;
	}
}
