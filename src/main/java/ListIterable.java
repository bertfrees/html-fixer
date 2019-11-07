public interface ListIterable<T> extends Iterable<T> {

	@Override
	public ListIterator<T> iterator();

}
