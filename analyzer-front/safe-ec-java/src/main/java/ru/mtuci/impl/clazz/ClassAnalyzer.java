package ru.mtuci.impl.clazz;

import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import lombok.*;
import ru.mtuci.impl.Analyzer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class ClassAnalyzer extends Analyzer {
    public static final String EXTENSION = ".class";
    
    public ClassAnalyzer(Path path) {
        super(path);
    }

    @Override
    @SneakyThrows
    public void analyze() {
        try (var is = new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ))) {
            var classFile = new ClassFile(new DataInputStream(is));
            for (MethodInfo method : classFile.getMethods()) {
                var iterator = method.getCodeAttribute().iterator();
                var variables = new HashMap<String, VariableValue>();
                var possibleCreations = new Stack<List<String>>();
                while (iterator.hasNext()) {
                    int address = iterator.next();
                    int opcode = iterator.byteAt(address);
                    if (Opcode.NEW == opcode) {
                        int i = iterator.s16bitAt(address + 1);
                        String classInfo = method.getConstPool().getClassInfo(i);
                        if (classInfo != null) {
                            
                        }
                    }
                }
            }
        }
    }
    
    @Getter
    @Setter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    private static class VariableValue {
        private Object value;
        
        public static VariableValue value(Object val) {
            return new VariableValue(val);
        }
        
        public static VariableValue unresolved() {
            return new VariableValue();
        }
    }
    
    private record ObjectCreation(String className) {
        private static final List<List<String>> knownSignatures = new ArrayList<>();
        
//        public static ObjectCreation newRecord(String className) {
//            
//        }
    }
}
