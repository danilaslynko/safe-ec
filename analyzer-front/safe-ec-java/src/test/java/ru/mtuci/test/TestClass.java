package ru.mtuci.test;

import io.churchkey.ec.Curve;
import lombok.SneakyThrows;

import java.security.KeyStore;

public class TestClass
{
    // OK
    public static void testResolveByName()
    {
        var name = "secp256k1";
        name = name = name = name = name = name = name = name;
        var secp256k1 = Curve.resolve(name);
        System.out.println(secp256k1);
    }

    // OK
    public static void testResolveByName2()
    {
        var name1 = "secp256k1";
        var name2 = name1;
        name1 = null;
        var secp256k1 = Curve.resolve(name2);
        System.out.println(secp256k1);
    }

    // Complex value, NOK
    public static void testResolveByName3()
    {
        String name;
        if ("string".compareTo("str") > 0)
            name = "secp256k1";
        else
            name = "secp256r1";

        var secp256k1 = Curve.resolve(name);
        System.out.println(secp256k1);
    }

    @SneakyThrows
    public static void testCryptoProKeystore()
    {
        var keyStore = KeyStore.getInstance("HDImageStore", "JCP");
        keyStore.load(null, null);
    }
}
