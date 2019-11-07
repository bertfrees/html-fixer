import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

abstract class MemoizingIterator<T> implements Iterator<T>, ListIterator<T> {

	private final List<T> list;
	private int i = 0;
	private boolean done = false;

	public MemoizingIterator(List<T> list) {
		this.list = list;
	}

	public abstract T computeNext();

	public boolean hasNext() {
		if (i < list.size())
			return true;
		if (done)
			return false;
		try {
			list.add(computeNext());
			return true;
		} catch (NoSuchElementException e) {
			done = true;
			return false;
		}
	}

	public boolean hasPrevious() {
		return i > 0;
	}

	public T next() {
		if (!hasNext())
			throw new NoSuchElementException();
		return list.get(i++);
	}

	public T previous() {
		if (!hasPrevious())
			throw new NoSuchElementException();
		return list.get(--i);
	}

	public int nextIndex() {
		return i;
	}

	public int previousIndex() {
		return i - 1;
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}

	public void set(T e) {
		throw new UnsupportedOperationException();
	}

	public void add(T e) {
		throw new UnsupportedOperationException();
	}

	// assumes that T is immutable
	@SuppressWarnings("unchecked")
	@Override
	public MemoizingIterator<T> clone() {
		try {
			return (MemoizingIterator<T>)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new InternalError("coding error");
		}
	}

	public static <T> ListIterable<T> iterable(Supplier<T> supplier) {
		return new ListIterable<T>() {
			List<T> list = new ArrayList<>();
			public ListIterator<T> iterator() {
				return new MemoizingIterator<T>(list) {
					public T computeNext() {
						return supplier.get();
					}
				};
			}
		};
	}
}
