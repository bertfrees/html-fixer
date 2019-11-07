import cz.vutbr.web.domassign.StyleMap;

public class Document {

	private final Element root;

	Document(org.w3c.dom.Document doc, StyleMap style) {
		this.root = new Element(null, doc.getDocumentElement(), style);
	}

	public Element root() {
		return root;
	}

	@Override
	public String toString() {
		return super.toString();
	}
}
