package com.wuman.androidimageloader.util.concurrent;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingStack<E> extends LinkedBlockingDeque<E> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1952633051250312858L;

	public LinkedBlockingStack() {
		super();
	}

	public LinkedBlockingStack(Collection<? extends E> c) {
		super(c);
	}

	public LinkedBlockingStack(int capacity) {
		super(capacity);
	}

	@Override
	public E remove() {
		return removeLast();
	}

	@Override
	public E poll() {
		return pollLast();
	}

	@Override
	public E take() throws InterruptedException {
		return takeLast();
	}

	@Override
	public E poll(long timeout, TimeUnit unit) throws InterruptedException {
		return pollLast(timeout, unit);
	}

	@Override
	public E element() {
		return getLast();
	}

	@Override
	public E peek() {
		return peekLast();
	}

	@Override
	public Iterator<E> iterator() {
		return super.descendingIterator();
	}

	@Override
	public Iterator<E> descendingIterator() {
		return super.iterator();
	}

}
