package ru.mtuci.utils;

import java.util.function.Function;

public abstract class Either<A, B>
{
    private Either() {}

    public abstract <R> R map(Function<A, R> left, Function<B, R> right);

    public static <A, B> Either<A, B> left(A value)
    {
        return new Either<>()
        {
            @Override
            public <R> R map(Function<A, R> left, Function<B, R> right)
            {
                return left.apply(value);
            }
        };
    }

    public static <A, B> Either<A, B> right(B value)
    {
        return new Either<>()
        {
            @Override
            public <R> R map(Function<A, R> left, Function<B, R> right)
            {
                return right.apply(value);
            }
        };
    }

    public A fromLeft(A defaultValue)
    {
        return fromLeft(b -> defaultValue);
    }

    public A fromLeft(Function<B, A> right)
    {
        return map(Function.identity(), right);
    }
}
