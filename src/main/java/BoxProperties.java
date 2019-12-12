import java.util.Set;

import com.google.common.collect.ImmutableSet;

public interface BoxProperties {

	public String display();
	public String backgroundColor();
	public String visibility();
	public int top();
	public int right();
	public int bottom();
	public int left();

	public Set<String> keySet = ImmutableSet.of("display",
	                                            "background-color",
	                                            "visibility",
	                                            "top",
	                                            "right",
	                                            "bottom",
	                                            "left");
	public Object get(String prop);

}
