package me.micartey.jairo.asm;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import me.micartey.jairo.annotation.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

public class JairoClassVisitor extends ClassVisitor {

  private final Class<?> target;
  private final Field fieldAnnotation;
  private String className;
  private boolean fieldAdded = false;

  public JairoClassVisitor(ClassVisitor cv, Class<?> target) {
    super(Opcodes.ASM9, cv);
    this.target = target;
    this.fieldAnnotation = target.getAnnotation(Field.class);
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    this.className = name;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitEnd() {
    if (fieldAnnotation != null && !fieldAdded) {
      addField();
    }
    super.visitEnd();
  }

  private void addField() {
    int access = fieldAnnotation.isPrivate() ? Opcodes.ACC_PRIVATE : Opcodes.ACC_PUBLIC;
    if (fieldAnnotation.isFinal()) {
      access |= Opcodes.ACC_FINAL;
    }

    String targetDescriptor = Type.getDescriptor(target);
    FieldVisitor fv = cv.visitField(access, fieldAnnotation.value(), targetDescriptor, null, null);
    if (fv != null) {
      fv.visitEnd();
    }
    fieldAdded = true;
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

    if (mv == null) return null;

    if (name.equals("<init>") && fieldAnnotation != null) {
      return new ConstructorFieldInitAdapter(
          mv, access, name, descriptor, className, target, fieldAnnotation);
    }

    for (Method method : target.getMethods()) {
      if (!method.isAnnotationPresent(Overwrite.class)) continue;

      Name nameAnnotation = method.getAnnotation(Name.class);
      String targetMethodName = nameAnnotation != null ? nameAnnotation.value() : method.getName();

      if (!name.equals(targetMethodName)) continue;

      Parameter paramAnnotation = method.getAnnotation(Parameter.class);
      if (paramAnnotation != null && !matchDescriptor(descriptor, paramAnnotation.value()))
        continue;

      Overwrite overwrite = method.getAnnotation(Overwrite.class);
      Optional<Return> returns = Optional.ofNullable(method.getAnnotation(Return.class));
      Optional<Field> field = Optional.ofNullable(target.getAnnotation(Field.class));

      return new JairoMethodAdapter(
          mv,
          access,
          name,
          descriptor,
          className,
          target,
          method,
          overwrite,
          returns.isPresent(),
          field.map(Field::value).orElse(null));
    }

    return mv;
  }

  private boolean matchDescriptor(String descriptor, Class<?>[] paramTypes) {
    String expected = buildDescriptor(paramTypes);
    String actual = descriptor.substring(0, descriptor.indexOf(')') + 1);
    return actual.equals(expected);
  }

  private String buildDescriptor(Class<?>[] paramTypes) {
    StringBuilder sb = new StringBuilder("(");
    for (Class<?> type : paramTypes) {
      sb.append(Type.getDescriptor(type));
    }
    sb.append(")");
    return sb.toString();
  }

  private static class ConstructorFieldInitAdapter extends AdviceAdapter {
    private final String className;
    private final Class<?> target;
    private final Field fieldAnnotation;

    protected ConstructorFieldInitAdapter(
        MethodVisitor mv,
        int access,
        String name,
        String descriptor,
        String className,
        Class<?> target,
        Field fieldAnnotation) {
      super(Opcodes.ASM9, mv, access, name, descriptor);
      this.className = className;
      this.target = target;
      this.fieldAnnotation = fieldAnnotation;
    }

    @Override
    protected void onMethodExit(int opcode) {
      if (opcode != ATHROW) {
        initializeField();
      }
    }

    private void initializeField() {
      mv.visitVarInsn(ALOAD, 0);

      mv.visitTypeInsn(NEW, Type.getInternalName(target));
      mv.visitInsn(DUP);

      boolean hasThisConstructor =
          Arrays.stream(target.getConstructors()).anyMatch(c -> c.getParameterCount() == 1);

      if (hasThisConstructor) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(
            INVOKESPECIAL, Type.getInternalName(target), "<init>", "(L" + className + ";)V", false);
      } else {
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(target), "<init>", "()V", false);
      }

      mv.visitFieldInsn(PUTFIELD, className, fieldAnnotation.value(), Type.getDescriptor(target));
    }
  }

  private static class JairoMethodAdapter extends AdviceAdapter {
    private final String className;
    private final Class<?> target;
    private final Method hookMethod;
    private final Overwrite overwrite;
    private final boolean allowReturn;
    private final String fieldName;

    protected JairoMethodAdapter(
        MethodVisitor mv,
        int access,
        String name,
        String descriptor,
        String className,
        Class<?> target,
        Method hookMethod,
        Overwrite overwrite,
        boolean allowReturn,
        String fieldName) {
      super(Opcodes.ASM9, mv, access, name, descriptor);
      this.className = className;
      this.target = target;
      this.hookMethod = hookMethod;
      this.overwrite = overwrite;
      this.allowReturn = allowReturn;
      this.fieldName = fieldName;
    }

    @Override
    protected void onMethodEnter() {
      if (overwrite.value() == Overwrite.Type.BEFORE) {
        injectCall();
      }
    }

    @Override
    protected void onMethodExit(int opcode) {
      if (overwrite.value() == Overwrite.Type.AFTER && opcode != ATHROW) {
        injectCall();
      }
    }

    @Override
    public void visitCode() {
      super.visitCode();
      if (overwrite.value() == Overwrite.Type.REPLACE) {
        injectCall();
        Type returnType = Type.getReturnType(methodDesc);
        if (returnType.getSort() == Type.VOID) {
          mv.visitInsn(RETURN);
        } else if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
          mv.visitInsn(ARETURN);
        } else if (returnType.getSort() == Type.INT
            || returnType.getSort() == Type.BOOLEAN
            || returnType.getSort() == Type.CHAR
            || returnType.getSort() == Type.BYTE
            || returnType.getSort() == Type.SHORT) {
          mv.visitInsn(IRETURN);
        } else if (returnType.getSort() == Type.LONG) {
          mv.visitInsn(LRETURN);
        } else if (returnType.getSort() == Type.FLOAT) {
          mv.visitInsn(FRETURN);
        } else if (returnType.getSort() == Type.DOUBLE) {
          mv.visitInsn(DRETURN);
        }
      }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitMaxs(maxStack, maxLocals);
      }
    }

    @Override
    public void visitInsn(int opcode) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitInsn(opcode);
      }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitIntInsn(opcode, operand);
      }
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitVarInsn(opcode, varIndex);
      }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitTypeInsn(opcode, type);
      }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitFieldInsn(opcode, owner, name, descriptor);
      }
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitJumpInsn(opcode, label);
      }
    }

    @Override
    public void visitLabel(Label label) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitLabel(label);
      }
    }

    @Override
    public void visitLdcInsn(Object value) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitLdcInsn(value);
      }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitIincInsn(varIndex, increment);
      }
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
      }
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitLookupSwitchInsn(dflt, keys, labels);
      }
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitMultiANewArrayInsn(descriptor, numDimensions);
      }
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitTryCatchBlock(start, end, handler, type);
      }
    }

    @Override
    public void visitLocalVariable(
        String name, String descriptor, String signature, Label start, Label end, int index) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitLocalVariable(name, descriptor, signature, start, end, index);
      }
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitLineNumber(line, start);
      }
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
      if (overwrite.value() != Overwrite.Type.REPLACE) {
        super.visitFrame(type, numLocal, local, numStack, stack);
      }
    }

    private void injectCall() {
      if (fieldName != null) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, fieldName, Type.getDescriptor(target));
      } else {
        mv.visitTypeInsn(NEW, Type.getInternalName(target));
        mv.visitInsn(DUP);

        boolean hasThisConstructor =
            Arrays.stream(target.getConstructors()).anyMatch(c -> c.getParameterCount() == 1);

        if (hasThisConstructor) {
          mv.visitVarInsn(ALOAD, 0);
          mv.visitMethodInsn(
              INVOKESPECIAL,
              Type.getInternalName(target),
              "<init>",
              "(L" + className + ";)V",
              false);
        } else {
          mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(target), "<init>", "()V", false);
        }
      }

      Type[] argTypes = Type.getArgumentTypes(methodDesc);
      int varIndex = 1;
      for (int i = 0; i < argTypes.length; i++) {
        mv.visitVarInsn(argTypes[i].getOpcode(ILOAD), varIndex);
        varIndex += argTypes[i].getSize();
      }

      String hookDescriptor = Type.getMethodDescriptor(hookMethod);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, Type.getInternalName(target), hookMethod.getName(), hookDescriptor, false);

      if (!allowReturn && hookMethod.getReturnType() != void.class) {
        Type returnType = Type.getType(hookMethod.getReturnType());
        if (returnType.getSize() == 2) {
          mv.visitInsn(POP2);
        } else {
          mv.visitInsn(POP);
        }
      }
    }
  }
}
