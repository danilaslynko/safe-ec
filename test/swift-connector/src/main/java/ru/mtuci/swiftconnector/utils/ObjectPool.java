package ru.mtuci.swiftconnector.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ObjectPool<T>
{
	private final List<T> objects = new ArrayList<>();
	private final Supplier<T> creator;
	
	public ObjectPool(Supplier<T> creator)
	{
		this.creator = creator;
	}
	
	public PooledObject get()
	{
		synchronized (this)
		{
			if (!objects.isEmpty())
				return new PooledObject(objects.remove(objects.size() - 1));
		}
		return new PooledObject(creator.get());
	}
	
	public synchronized void release(T t)
	{
		if (t != null)
			objects.add(t);
	}

	@RequiredArgsConstructor
	public class PooledObject implements AutoCloseable
	{
		@Getter
		private final T object;

		@Override
		public void close()
		{
			release(object);
		}
	}
}
