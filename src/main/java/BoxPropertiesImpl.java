import java.util.ArrayList;
import java.util.List;

import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.CSSProperty.BackgroundColor;
import cz.vutbr.web.css.CSSProperty.Display;
import cz.vutbr.web.css.CSSProperty.Margin;
import cz.vutbr.web.css.CSSProperty.Visibility;
import cz.vutbr.web.css.TermColor;
import cz.vutbr.web.css.TermLengthOrPercent;

/**
 * Used values of properties
 */
public class BoxPropertiesImpl extends Style implements BoxProperties {

	private final BoxPropertiesImpl parentBox;

	BoxPropertiesImpl(Style cascaded, BoxPropertiesImpl parentBox) {
		super(cascaded);
		this.parentBox = parentBox;
	}

	public Object get(String prop) {
		if ("display".equals(prop))
			return display();
		else if ("background-color".equals(prop))
			return backgroundColor();
		else if ("visibility".equals(prop))
			return visibility();
		else
			throw new IllegalArgumentException();
	}

	private Display displayProp = null;
	private Display displayProp() {
		if (displayProp == null) {
			if (parentBox != null && parentBox.displayProp() == Display.NONE)
				displayProp = Display.NONE;
			else
				displayProp = getSpecifiedProperty("display", Display.class).prop;
		}
		return displayProp;
	}

	public String display() {
		return displayProp().toString();
	}
	private String backgroundColor = null;
	public String backgroundColor() {
		if (backgroundColor == null) {
			Property<BackgroundColor> p = getSpecifiedProperty("background-color", BackgroundColor.class);
			switch (p.prop) {
			case color:
				return ((TermColor)p.val).toString();
			case TRANSPARENT:
				if (parentBox != null)
					return parentBox.backgroundColor();
				return null;
			default:
				throw new RuntimeException("coding error");
			}
		}
		return backgroundColor;
	}

	private String visibility = null;
	public String visibility() {
		if (visibility == null) {
			Property<Visibility> p = getSpecifiedProperty("visibility", Visibility.class);
			switch (p.prop) {
			case COLLAPSE:
			case HIDDEN:
				return "hidden";
			case VISIBLE:
				return "visible";
			default:
				throw new RuntimeException("coding error");
			}
		}
		return visibility;
	}

	public Style relativize(BoxPropertiesImpl base) {
		if (parentBox == base)
			return this;
		else {
			Style ifEmpty = new Style((Iterable<Style.Property<CSSProperty>>)null, base);
			List<Style.Property<CSSProperty>> relative = new ArrayList<>();
			for (String p : Style.supportedCss.getDefinedPropertyNames()) {
				Style.Property<CSSProperty> a = getProperty(p, true, false); // don't concretize initial
				Style.Property<CSSProperty> b = ifEmpty.getProperty(p, true, false);
				// special handling of initial because the defaults may be inconsistent across browsers
				if (a.prop.equalsInitial()) {
					if (b.prop.equalsInitial())
						;
					else
						relative.add(getSpecifiedProperty(p));
				} else if (b.prop.equalsInitial())
					relative.add(a);
				else if (a.prop instanceof Margin
				         && a.val != null && ((TermLengthOrPercent)a.val).getValue() == 0f
				         && b.val != null && ((TermLengthOrPercent)b.val).getValue() == 0f)
					// unit doesn't matter if it's 0
					continue;
				else if (!a.equals(b))
					relative.add(a);
			}
			return new Style(relative, base);
		}
	}
}
