package ru.mtuci.swiftconnector.service.crypto;

import org.apache.jcp.xml.dsig.internal.dom.DOMSubTreeData;
import org.w3c.dom.Node;

import javax.xml.crypto.Data;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.URIReference;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.XMLCryptoContext;

public class NoUriDereferencer implements URIDereferencer
{
    private final URIDereferencer defaultDereferencer;
    private final Node noUriNode;

    public NoUriDereferencer(Node noUriNode, URIDereferencer defaultDereferencer)
    {
        if (noUriNode == null)
            throw new IllegalArgumentException("No URI node is null");

        this.defaultDereferencer = defaultDereferencer;
        this.noUriNode = noUriNode;
    }

    @Override
    public Data dereference(URIReference uriRef, XMLCryptoContext ctx) throws URIReferenceException
    {
        if (uriRef.getURI() == null || uriRef.getURI().isBlank())
            return new DOMSubTreeData(noUriNode, false);

        return defaultDereferencer.dereference(uriRef, ctx);
    }
}
