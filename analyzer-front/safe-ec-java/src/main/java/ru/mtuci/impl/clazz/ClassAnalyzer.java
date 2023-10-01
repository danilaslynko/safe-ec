package ru.mtuci.impl.clazz;

import io.churchkey.ec.Curve;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
import ru.mtuci.net.Request;
import ru.mtuci.net.Response;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    protected List<RequestDto> makeRequests()
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
                                || (owner.equals(Curve.class.getName()) && StringUtils.equalsAny(methodInsn.name, "resolve", "valueOf")))
                        {
                            var resp = checkStringNameOperand(frame);
                            if (resp != null)
                            {
                                Integer l = lineNumber.getValue();
                                requests.add(new RequestDto(resp, () -> newLocation.apply(l)));
                            }
                        }
                    }
                    else if (instr instanceof FieldInsnNode fieldInsn)
                    {
                        if (instr.getOpcode() == GETSTATIC || instr.getOpcode() == GETFIELD)
                        {
                            var owner = fieldInsn.owner.replace('/', '.');
                            if ((CURVE_HOLDERS.contains(owner) || owner.equals(Curve.class.getName())) && Utils.curveByName(fieldInsn.name) != null)
                            {
                                var resp = request(Request.Type.Named, fieldInsn.name);
                                if (resp != null)
                                {
                                    Integer l = lineNumber.getValue();
                                    requests.add(new RequestDto(resp, () -> newLocation.apply(l)));
                                }
                            }
                        }
                    }
                }
            }
        }
        return requests;
    }

    private Future<Response> checkStringNameOperand(Frame<SourceValue> frame)
    {
        var arg = frame.getStack(frame.getStackSize() - 1);
        if (arg.insns.size() != 1)
            return null;

        var argSrc = arg.insns.iterator().next();
        if (argSrc instanceof LdcInsnNode ldc)
        {
            if (ldc.cst instanceof String name && Utils.curveByName(name) != null)
                return request(Request.Type.Named, name);
        }
        else if (argSrc instanceof VarInsnNode var)
        {
            var local = frame.getLocal(var.var);
            if (local.insns.size() != 1)
                return null;

            var localSrc = local.insns.iterator().next();
            if (localSrc instanceof LdcInsnNode ldc)
            {
                if (ldc.cst instanceof String name && Utils.curveByName(name) != null)
                    return request(Request.Type.Named, name);
            }
        }
        return null;
    }
}
