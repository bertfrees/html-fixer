public class Fragment {

	// 0-based index of start block
	public final int startBlockIndex;

	// if non-negative, 0-based index of the start inline unit within the start block
	public final int startInlineIndex;

	// number of blocks or inline units in the range
	public final int size;

	public Fragment(int startBlockIndex) {
		this(startBlockIndex, -1, 1);
	}

	public Fragment(int startBlockIndex, int size) {
		this(startBlockIndex, -1, size);
	}

	public Fragment(int startBlockIndex, int startInlineIndex, int size) {
		if (startBlockIndex < 0) throw new IllegalArgumentException();
		if (size < 1) throw new IllegalArgumentException();
		this.startBlockIndex = startBlockIndex;
		this.startInlineIndex = startInlineIndex;
		this.size = size;
	}
}
