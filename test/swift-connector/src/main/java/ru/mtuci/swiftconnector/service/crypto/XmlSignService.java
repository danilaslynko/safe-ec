package ru.mtuci.swiftconnector.service.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import ru.mtuci.swiftconnector.CryptoConfig;
import ru.mtuci.swiftconnector.utils.Utils;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class XmlSignService
{
    public static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";

    private final CryptoConfig cryptoConfig;
    private final KeyStoreLoader keyStoreLoader;

    private volatile CertsDirectory certsDirectory;

    static
    {
        org.apache.xml.security.Init.init();
    }

    public String signXml(String xml)
    {
        try
        {
            var doc = Utils.withDocumentBuilder(b -> b.parse(new InputSource(new StringReader(xml))));
            var fac = XMLSignatureFactory.getInstance("DOM");

            var kif = fac.getKeyInfoFactory();
            var keyStore = keyStoreLoader.loadOrCreate(cryptoConfig.getKeyStore(), cryptoConfig.getKeyStorePassword(), cryptoConfig.getKeyAlias());
            var cert = (X509Certificate) keyStore.getCertificate(cryptoConfig.getKeyAlias());
            var x509data = kif.newX509Data(Collections.singletonList(kif.newX509IssuerSerial(cert.getIssuerX500Principal().toString(), cert.getSerialNumber())));
            var keyInfoId = "_" + UUID.randomUUID();
            var ki = kif.newKeyInfo(Collections.singletonList(x509data), keyInfoId);

            var refs = new ArrayList<Reference>();

            var canonicalizationMethod = fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (XMLStructure) null);
            var ref1 = fac.newReference("#" + keyInfoId,
                    fac.newDigestMethod(DigestMethod.SHA256, null),
                    Collections.singletonList(canonicalizationMethod),
                    null, null);
            refs.add(ref1);

            var signedpropsId = "_" + UUID.randomUUID() + "-signedprops";

            var ref2 = fac.newReference("#" + signedpropsId,
                    fac.newDigestMethod(DigestMethod.SHA256, null),
                    Collections.singletonList(canonicalizationMethod),
                    "http://uri.etsi.org/01903/v1.3.2#SignedProperties", null);
            refs.add(ref2);

            var ref3 = fac.newReference(null,
                    fac.newDigestMethod(DigestMethod.SHA256, null),
                    Collections.singletonList(canonicalizationMethod),
                    null, null);
            refs.add(ref3);

            var si = fac.newSignedInfo(
                    canonicalizationMethod,
                    fac.newSignatureMethod(SignatureMethod.ECDSA_SHA256, null), refs);

            var sgntr = findOrCreateSignatureNode(doc);
            var dsc = new DOMSignContext(keyStore.getKey(cryptoConfig.getKeyAlias(), cryptoConfig.getKeyStorePassword().toCharArray()), sgntr);

            dsc.putNamespacePrefix(XMLSignature.XMLNS, "ds");

            var qpElement = doc.createElementNS(XADES_NS, "xades:QualifyingProperties");
            qpElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xades", XADES_NS);

            var spElement = doc.createElementNS(XADES_NS, "xades:SignedProperties");
            spElement.setAttributeNS(null, "Id", signedpropsId);
            dsc.setIdAttributeNS(spElement, null, "Id");
            spElement.setIdAttributeNS(null, "Id", true);
            qpElement.appendChild(spElement);

            var sspElement = doc.createElementNS(XADES_NS, "xades:SignedSignatureProperties");
            spElement.appendChild(sspElement);

            var signingTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());

            var stElement = doc.createElementNS(XADES_NS, "xades:SigningTime");
            stElement.appendChild(doc.createTextNode(signingTime));
            sspElement.appendChild(stElement);

            var object = fac.newXMLObject(Collections.singletonList(new DOMStructure(qpElement)), null, null, null);
            var objects = Collections.singletonList(object);

            var noUriNodes = doc.getElementsByTagName("Document");
            if (noUriNodes.getLength() == 0)
            {
                throw new RuntimeException("mandatory element Document is missing in the document to be signed");
            }
            var noUriNode = noUriNodes.item(0);
            var noUriDereferencer = new NoUriDereferencer(noUriNode, fac.getURIDereferencer());
            dsc.setURIDereferencer(noUriDereferencer);

            XMLSignature signature = fac.newXMLSignature(si, ki, objects, null, null);
            signature.sign(dsc);

            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
            return xml;
        }
    }

    public void verifySignature(String xml)
    {
        var doc = Utils.withDocumentBuilder(b -> b.parse(new InputSource(new StringReader(xml))));
        var nodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nodes == null || nodes.getLength() == 0)
            throw new RuntimeException("Signature is missing in the document");

        var fac = XMLSignatureFactory.getInstance("DOM");
        var docNodes = doc.getElementsByTagName("Document");
        var dereferencer = new NoUriDereferencer(docNodes.item(0), fac.getURIDereferencer());

        var nodeSignature = (Element) nodes.item(0);

        try
        {
            var keySelector = new DirectoryKeySelector(getCertsDirectory());
            var valContext = new DOMValidateContext(keySelector, nodeSignature);
            valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            valContext.setURIDereferencer(dereferencer);

            // Java 1.7.0_25+ complicates validation of ds:Object/QualifyingProperties/SignedProperties
            // See details at https://bugs.openjdk.java.net/browse/JDK-8019379
            //
            // One of the solutions is to register the Id attribute using the DOMValidateContext.setIdAttributeNS
            // method before validating the signature
            var nl = nodeSignature.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "SignedProperties");
            if (nl.getLength() == 0)
                throw new XMLSignatureException("SignedProperties is missing in signature");

            var elemSignedProps = (Element) nl.item(0);
            valContext.setIdAttributeNS(elemSignedProps, null, "Id");

            // always enable cacheReference to log pre digest data in case of errors
            valContext.setProperty("javax.xml.crypto.dsig.cacheReference", Boolean.TRUE);
            var signature = fac.unmarshalXMLSignature(valContext);
            var coreValidity = signature.validate(valContext);
            if (coreValidity)
            {
                var signerCert = keySelector.getSelected();

                var stl = elemSignedProps.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "SigningTime");
                if (stl.getLength() == 0)
                    throw new XMLSignatureException("SigningTime is missing in signature");

                var signingTimeElem = (Element) stl.item(0);
                var signingTime = Date.from(ZonedDateTime.parse(signingTimeElem.getTextContent()).toInstant());
                if (signingTime.before(signerCert.getNotBefore()) || signingTime.after(signerCert.getNotAfter()))
                    throw new XMLSignatureException("SigningTime is outside of certificate validity ");
            }
            else
            {
                log.error("Signature failed core validation");
                var sv = signature.getSignatureValue().validate(valContext);
                log.info("Signature validation status: {}", sv);
                var it = signature.getSignedInfo().getReferences().iterator();
                for (int j = 0; it.hasNext(); j++)
                {
                    var ref = it.next();
                    var refURI = ref.getURI();
                    var refValid = ref.validate(valContext);
                    log.info("ref[{}] validity status: {}, ref URI: [{}], ref type: [{}]",
                            j, refValid, refURI, ref.getType());
                    var calcDigValStr = digestToString(ref.getCalculatedDigestValue());
                    var expectedDigValStr = digestToString(ref.getDigestValue());
                    log.info("    Calc Digest: {}", calcDigValStr);
                    log.info("Expected Digest: {}", expectedDigValStr);
                }
                throw new XMLSignatureException("XML signature failed to validate");
            }
        }
        catch (MarshalException | XMLSignatureException | TransformerFactoryConfigurationError e)
        {
            log.error(e.getMessage(), e);
            ExceptionUtils.rethrow(e);
        }
    }

    private String digestToString(byte[] digest)
    {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest)
        {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1)
            {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    protected Node findOrCreateSignatureNode(Document doc)
    {
        String parentNodeName = "AppHdr";
        String nextNodeName = "Rltd";
        Node sgntrParent = null;
        Node sgntr = null;
        Node nextNode = null;
        NodeList sgntrParentList = doc.getElementsByTagName(parentNodeName);
        if (sgntrParentList.getLength() != 0)
            sgntrParent = sgntrParentList.item(0);

        if (sgntrParent == null)
            throw new RuntimeException("Mandatory element " + parentNodeName + " is missing in the document to be signed");

        NodeList appHdrChildList = sgntrParent.getChildNodes();
        for (int i = 0; i < appHdrChildList.getLength(); i++)
        {
            Node childNode = appHdrChildList.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE)
            {
                if (Objects.equals(childNode.getLocalName(), "Sgntr"))
                    sgntr = childNode;
                else if (Objects.equals(childNode.getLocalName(), nextNodeName))
                    nextNode = childNode;
            }
        }

        if (sgntr == null)
        {
            if (nextNode == null)
                sgntr = sgntrParent.appendChild(doc.createElementNS(sgntrParent.getNamespaceURI(), "Sgntr"));
            else
                sgntr = sgntrParent.insertBefore(doc.createElementNS(sgntrParent.getNamespaceURI(), "Sgntr"), nextNode);
        }

        return sgntr;
    }

    public CertsDirectory getCertsDirectory()
    {
        if (this.certsDirectory == null)
        {
            synchronized (this)
            {
                if (this.certsDirectory == null)
                    this.certsDirectory = new CertsDirectory(cryptoConfig.getCertsDir());
            }
        }
        return this.certsDirectory;
    }
}
