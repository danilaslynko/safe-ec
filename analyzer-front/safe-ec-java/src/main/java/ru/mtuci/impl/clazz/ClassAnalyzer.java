package ru.mtuci.impl.clazz;

import io.churchkey.asn1.Oid;
import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.bouncycastle.asn1.anssi.ANSSINamedCurves;
import org.bouncycastle.asn1.cryptopro.ECGOST3410NamedCurves;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X962NamedCurves;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import ru.mtuci.Utils;
import ru.mtuci.base.LocationMeta;
import ru.mtuci.base.RequestingAnalyzer;
import ru.mtuci.impl.crypto.CryptoProKeystoreAnalyzer;
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;

import java.io.BufferedInputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ClassAnalyzer extends RequestingAnalyzer implements Opcodes
{
    private static final int MAX_ITERS = 500;

    private static final List<String> CURVE_HOLDERS = Stream.of(
            Curve.class,
            X962NamedCurves.class,
            SECNamedCurves.class,
            ECNamedCurveTable.class,
            NISTNamedCurves.class,
            TeleTrusTNamedCurves.class,
            ANSSINamedCurves.class,
            ECGOST3410NamedCurves.class,
            GMNamedCurves.class,
            CustomNamedCurves.class,
            SECObjectIdentifiers.class
    ).map(Class::getCanonicalName).toList();

    public static final String EXTENSION = ".class";

    public ClassAnalyzer(Path path)
    {
        super(path);
    }

    @SneakyThrows
    @Override
    public List<RequestDto> makeRequests()
    {
        var requests = new ArrayList<RequestDto>();
        try (var is = new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ)))
        {
            var cr = new ClassReader(is);
            var cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_FRAMES);
            for (var method : cn.methods)
            {
                var analyzer = new Analyzer<>(new SourceInterpreter());
                var frames = analyzer.analyzeAndComputeMaxs(cn.name, method);
                var instructions = method.instructions;
                MutableInt lineNumber = new MutableInt();
                Function<Integer, LocationMeta> newLocation = l -> baseMeta()
                        .className(Type.getObjectType(cn.name).getClassName())
                        .funcName(method.name + "(" + Arrays.stream(Type.getArgumentTypes(method.desc)).map(Type::getClassName).collect(Collectors.joining(",")) + ")")
                        .linenumber(l)
                        .build();
                for (int i = 0; i < frames.length; i++)
                {
                    var instr = instructions.get(i);
                    if (instr instanceof LineNumberNode line)
                    {
                        lineNumber.setValue(line.line);
                        continue;
                    }

                    var frame = frames[i];
                    if (frame == null)
                        continue;

                    if (instr instanceof MethodInsnNode methodInsn)
                    {
                        var owner = Type.getObjectType(methodInsn.owner).getClassName();
                        var methodType = Type.getMethodType(methodInsn.desc);
                        var args = Arrays.stream(methodType.getArgumentTypes()).map(Type::getClassName).toList();
                        if (args.size() == 1 && args.get(0).equals(String.class.getName()) &&
                                CURVE_HOLDERS.contains(owner) && StringUtils.equalsAny(methodInsn.name, "getByName", "getByNameLazy")
                                || (owner.equals(Curve.class.getName()) && StringUtils.equalsAny(methodInsn.name, "resolve", "valueOf"))
                                || (owner.equals(ECGenParameterSpec.class.getName()) && StringUtils.equals(methodInsn.name, "<init>")))
                        {
                            var str = getLastInsnString(frame, instructions, frames);
                            Future<Response> resp = checkString(str);
                            if (resp != null)
                            {
                                Integer l = lineNumber.getValue();
                                requests.add(new RequestDto(resp, () -> newLocation.apply(l)));
                            }
                        }
                        else if (owner.equals(KeyStore.class.getName()) && methodInsn.name.equals("getInstance"))
                        {
                            var allArgs = frame.getStackSize();
                            var typeVar = frame.getStack(allArgs - args.size());
                            if (typeVar.insns.size() == 1)
                            {
                                var keyStoreType = getInsnString(frame, typeVar.insns.iterator().next(), instructions, frames, 0);
                                if (StringUtils.equalsAny(keyStoreType, "HDImageStore", "CertStore"))
                                {
                                    var keystoreAnalyzer = new CryptoProKeystoreAnalyzer(null, keyStoreType, null, null);
                                    requests.addAll(keystoreAnalyzer.makeRequests());
                                }
                            }
                        }
                    }
                }
            }
        }
        return requests;
    }

    private String getLastInsnString(Frame<SourceValue> frame, InsnList instructions, Frame<SourceValue>[] frames)
    {
        var arg = frame.getStack(frame.getStackSize() - 1);
        if (arg.insns.size() != 1)
            return null;

        return getInsnString(frame, arg.insns.iterator().next(), instructions, frames, 0);
    }

    private String getInsnString(Frame<SourceValue> frame, AbstractInsnNode insn, InsnList instructions, Frame<SourceValue>[] frames, int iter)
    {
        if (insn.getOpcode() == DUP)
        {
            insn = insn.getPrevious();
            var i = ArrayUtils.indexOf(frames, frame);
            frame = frames[i - 1];
        }

        if (iter > MAX_ITERS)
        {
            log.warn("Too complex value, skip");
            return null;
        }
        if (insn instanceof LdcInsnNode ldc)
        {
            if (ldc.cst instanceof String str)
                return str;
        }
        else if (insn instanceof FieldInsnNode field)
        {
            try
            {
                var owner = Class.forName(Type.getObjectType(field.owner).getClassName());
                var f = owner.getField(field.name);
                if (!Modifier.isStatic(f.getModifiers()))
                    return null;

                f.setAccessible(true);
                var result = f.get(null);
                if (result instanceof String str)
                    return str;
            }
            catch (Exception e)
            {
                log.warn("Cannot resolve field value {}", field, e);
            }
        }
        else if (insn instanceof VarInsnNode var)
        {
            var local = frame.getLocal(var.var);
            if (local.insns.size() != 1)
                return null;

            var localSrc = local.insns.iterator().next();
            if (localSrc instanceof VarInsnNode)
                localSrc = localSrc.getPrevious(); // Инструкция со значением, инициализирующим переменную

            var i = instructions.indexOf(localSrc);
            frame = frames[i];
            return getInsnString(frame, localSrc, instructions, frames, iter + 1);
        }
        return null;
    }

    private Future<Response> checkString(String str)
    {
        try
        {
            if (Utils.curveByName(str) != null)
                return request(Request.Type.Named, str);

            if (Curve.resolve(Oid.fromString(str)) != null)
                return request(Request.Type.OID, str);
        }
        catch (Exception ignored)
        {
        }

        return null;
    }
}
