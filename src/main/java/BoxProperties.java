import java.util.Set;

import com.google.common.collect.ImmutableSet;

public interface BoxProperties {

	public String display();
	public String backgroundColor();
	public String visibility();

	public Set<String> keySet = ImmutableSet.of("display",
	                                            "background-color",
	                                            "visibility");
	public Object get(String prop);

}
