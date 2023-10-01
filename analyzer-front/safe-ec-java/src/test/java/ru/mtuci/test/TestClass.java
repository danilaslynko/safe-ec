package ru.mtuci.test;

import io.churchkey.ec.Curve;

public class TestClass
{
    public static void testResolveByName()
    {
        var secp256k1 = Curve.resolve("secp256k1");
        System.out.println(secp256k1);
    }

    public static void testEnumConstant()
    {
        var secp256r1 = Curve.secp256r1;
        System.out.println(secp256r1);
    }
}
