import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.CSSProperty.Display;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.SupportedCSS;
import cz.vutbr.web.css.Term;

/**
 * Cascaded/specified/computed values of properties
 */
public class Style {

	final static SupportedCSS supportedCss = CSSFactory.getSupportedCSS();
	private final static Map<String,Property<CSSProperty>> initialValues = new HashMap<>();
	private final static Map<String,Boolean> inherited = new HashMap<>();
	private final static Map<String,Class<? extends CSSProperty>> propertyClasses = new HashMap<>();
	final static List<Property<CSSProperty>> BLOCK = new ArrayList<>();
	final static NodeData INLINE = null;
	static {
		for (String propName : supportedCss.getDefinedPropertyNames()) {
			CSSProperty defaultProp = supportedCss.getDefaultProperty(propName);
			initialValues.put(propName,
			                  new Property<CSSProperty>(propName,
			                                            defaultProp,
			                                            supportedCss.getDefaultValue(propName)));
			inherited.put(propName, defaultProp.inherited());
			propertyClasses.put(propName, defaultProp.getClass());
		}
		BLOCK.add(new Property<CSSProperty>("display", Display.BLOCK, null));
	}

	private static <P extends Enum<P> & CSSProperty> Property<P> INHERIT(String property, Class<P> propertyClass) {
		return new Property<P>(property, Enum.<P>valueOf(propertyClass, "INHERIT"), null);
	}

	private static <P extends Enum<P> & CSSProperty> Property<P> INITIAL(String property, Class<P> propertyClass) {
		return new Property<P>(property, Enum.<P>valueOf(propertyClass, "INITIAL"), null);
	}

	final Map<String,Property<CSSProperty>> cascaded;
	private final Style inheritFrom;

	Style(NodeData cascaded, Style inheritFrom) {
		if (cascaded != null) {
			this.cascaded = new HashMap<>();
			for (String p : cascaded.getPropertyNames())
				this.cascaded.put(p, new Property<CSSProperty>(p,
				                                               cascaded.getProperty(p, false),
				                                               cascaded.getValue(p, false)));
		} else
			this.cascaded = null;
		this.inheritFrom = inheritFrom;
	}

	Style(Iterable<Property<CSSProperty>> cascaded, Style inheritFrom) {
		if (cascaded != null) {
			this.cascaded = new HashMap<>();
			for (Property<CSSProperty> p : cascaded)
				this.cascaded.put(p.name, p);
		} else
			this.cascaded = null;
		this.inheritFrom = inheritFrom;
	}

	protected Style(Style style) {
		this.cascaded = style.cascaded;
		this.inheritFrom = style.inheritFrom;
	}

	protected Property<CSSProperty> getSpecifiedProperty(String property) {
		Property<CSSProperty> p = getProperty(property, true, true);
		if (p == Property.DUMMY_INHERIT || p == Property.DUMMY_INITIAL)
			throw new RuntimeException("coding error");
		return p;
	}

	protected <P extends Enum<P> & CSSProperty> Property<P> getSpecifiedProperty(String property, Class<P> propertyClass) {
		Property<CSSProperty> p = getProperty(property, propertyClass, true, true);
		if (p == Property.DUMMY_INHERIT)
			return INHERIT(property, propertyClass);
		else if (p == Property.DUMMY_INITIAL)
			return INITIAL(property, propertyClass);
		else
			return Property.<P>cast(p, propertyClass);
	}

	// note that this may return a Property<DummyCSSProperty>
	protected Property<CSSProperty> getProperty(String property,
	                                            boolean concretizeInherit,
	                                            boolean concretizeInitial) {
		Class<? extends CSSProperty> propertyClass = propertyClasses.get(property);
		if (propertyClass == null)
			throw new IllegalArgumentException("unsupported property: " + property);
		return getProperty(property, propertyClass, concretizeInherit, concretizeInitial);
	}

	private Property<CSSProperty> getProperty(String property,
	                                          Class<? extends CSSProperty> propertyClass,
	                                          boolean concretizeInherit,
	                                          boolean concretizeInitial) {
		Property<CSSProperty> p = cascaded != null ? cascaded.get(property) : null;
		boolean equalsInherit = false;
		boolean equalsInitial = false;
		if (p == null) {
			if (inherited.get(property))
				equalsInherit = true;
			else
				equalsInitial = true;
		}
		if ((equalsInherit || p != null && p.prop.equalsInherit()) && concretizeInherit) {
			if (inheritFrom != null)
				return inheritFrom.getProperty(property, propertyClass, concretizeInherit, concretizeInitial);
			else
				equalsInitial = true;
		}
		if ((equalsInitial || p.prop.equalsInitial()) && concretizeInitial)
			return initialValues.get(property);
		if (equalsInherit)
			return Property.DUMMY_INHERIT;
		else if (equalsInitial)
			return Property.DUMMY_INITIAL;
		else
			return p;
	}

	protected static class Property<P extends CSSProperty> {
		private final static Property<CSSProperty> DUMMY_INHERIT = new Property<>("dummy", DummyCSSProperty.INHERIT, null);
		private final static Property<CSSProperty> DUMMY_INITIAL = new Property<>("dummy", DummyCSSProperty.INHERIT, null);
		final String name;
		final P prop;
		final Term<?> val;
		Property(String name, P prop, Term<?> val) {
			this.name = name;
			this.prop = prop;
			this.val = val;
		}
		@SuppressWarnings("unchecked")
		static <P extends CSSProperty> Property<P> cast(Property<? extends CSSProperty> property, Class<P> clazz) {
			return (Property<P>)property;
		}
		@Override
		public String toString() {
			return name + ": " + (val != null ? val : prop);
		}
		@Override
		public int hashCode() {
			return toString().hashCode();
		}
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null)
				return false;
			if (getClass() != o.getClass())
				return false;
			return toString().equals(o.toString());
		}
	}

	private static enum DummyCSSProperty implements CSSProperty {
		INHERIT, INITIAL;
		public boolean equalsInherit() {
			return this == INHERIT;
		}
		public boolean equalsInitial() {
			return this == INITIAL;
		}
		public boolean inherited() {
			throw new UnsupportedOperationException();
		}
	}
}
