import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.xml.transform.stream.StreamSource;

import cz.vutbr.web.css.CSSException;
import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.MediaSpec;
import cz.vutbr.web.css.NetworkProcessor;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.csskit.DefaultNetworkProcessor;
import cz.vutbr.web.domassign.Analyzer;
import cz.vutbr.web.domassign.SingleMapNodeData;
import cz.vutbr.web.domassign.StyleMap;
import net.sf.saxon.dom.DocumentOverNodeInfo;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;

public class Parser {

	private final static URL DEFAULT_CSS = Parser.class.getResource("html4.css");

	static {
		CSSFactory.registerNodeDataInstance(MyNodeData.class);
	}

	public static Document parse(InputStream document, URL base) {
		try {
			org.w3c.dom.Document doc = (org.w3c.dom.Document)DocumentOverNodeInfo.wrap(
				new Processor(false).newDocumentBuilder()
					.build(new StreamSource(document, base.toString()))
					.getUnderlyingNode());
			NetworkProcessor network = new DefaultNetworkProcessor();
			StyleSheet stylesheet = CSSFactory.parse(DEFAULT_CSS, network, null);
			stylesheet = CSSFactory.getUsedStyles(doc, null, base, new MediaSpec("screen"), network, stylesheet);
			StyleMap style = new Analyzer(stylesheet).evaluateDOM(doc, "screen", false);
			return new Document(doc, style);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (SaxonApiException e) {
			throw new RuntimeException(e);
		} catch (CSSException e) {
			throw new RuntimeException(e);
		}
	}

	public static class MyNodeData extends SingleMapNodeData {
		public NodeData concretize() {
			return this;
		}
	}
}
