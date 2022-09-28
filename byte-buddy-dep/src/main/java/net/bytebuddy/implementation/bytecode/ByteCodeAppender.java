package net.bytebuddy.implementation.bytecode;

import lombok.EqualsAndHashCode;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.Implementation;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An appender that generates the byte code for a given method. This is done by writing the byte code instructions to
 * the given ASM {@link org.objectweb.asm.MethodVisitor}.
 * <p>&nbsp;</p>
 * The {@code ByteCodeAppender} is not allowed to write
 * annotations to the method or call the {@link org.objectweb.asm.MethodVisitor#visitCode()},
 * {@link org.objectweb.asm.MethodVisitor#visitMaxs(int, int)} or {@link org.objectweb.asm.MethodVisitor#visitEnd()}
 * methods which is both done by the entity delegating the call to the {@code ByteCodeAppender}. This is done in order
 * to allow for the concatenation of several byte code appenders and therefore a more modular description of method
 * implementations.
 */
public interface ByteCodeAppender {

    /**
     * Applies this byte code appender to a type creation process.
     *
     * @param methodVisitor         The method visitor to which the byte code appender writes its code to.
     * @param implementationContext The implementation context of the current type creation process.
     * @param instrumentedMethod    The method that is the target of the instrumentation.
     * @return The required size for the applied byte code to run.
     */
    Size apply(MethodVisitor methodVisitor,
               Implementation.Context implementationContext,
               MethodDescription instrumentedMethod);

    /**
     * An immutable description of both the operand stack size and the size of the local variable array that is
     * required to run the code generated by this {@code ByteCodeAppender}.
     */
    @EqualsAndHashCode
    class Size {

        /**
         * The size of the operand stack.
         */
        private final int operandStackSize;

        /**
         * The size of the local variable array.
         */
        private final int localVariableSize;

        /**
         * @param operandStackSize  The operand stack size that is required for running given byte code.
         * @param localVariableSize The local variable array size that is required for running given byte code.
         */
        public Size(int operandStackSize, int localVariableSize) {
            this.operandStackSize = operandStackSize;
            this.localVariableSize = localVariableSize;
        }

        /**
         * Returns the required operand stack size.
         *
         * @return The required operand stack size.
         */
        public int getOperandStackSize() {
            return operandStackSize;
        }

        /**
         * Returns the required size of the local variable array.
         *
         * @return The required size of the local variable array.
         */
        public int getLocalVariableSize() {
            return localVariableSize;
        }

        /**
         * Merges two sizes in order to describe the size that is required by both size descriptions.
         *
         * @param other The other size description.
         * @return A size description incorporating both size requirements.
         */
        public Size merge(Size other) {
            return new Size(Math.max(operandStackSize, other.operandStackSize), Math.max(localVariableSize, other.localVariableSize));
        }
    }

    /**
     * A compound appender that combines a given number of other byte code appenders.
     */
    @EqualsAndHashCode
    class Compound implements ByteCodeAppender {

        /**
         * The byte code appenders that are represented by this compound appender in their application order.
         */
        private final List<ByteCodeAppender> byteCodeAppenders;

        /**
         * Creates a new compound byte code appender.
         *
         * @param byteCodeAppender The byte code appenders to combine in their order.
         */
        public Compound(ByteCodeAppender... byteCodeAppender) {
            this(Arrays.asList(byteCodeAppender));
        }

        /**
         * Creates a new compound byte code appender.
         *
         * @param byteCodeAppenders The byte code appenders to combine in their order.
         */
        public Compound(List<? extends ByteCodeAppender> byteCodeAppenders) {
            this.byteCodeAppenders = new ArrayList<ByteCodeAppender>();
            for (ByteCodeAppender byteCodeAppender : byteCodeAppenders) {
                if (byteCodeAppender instanceof Compound) {
                    this.byteCodeAppenders.addAll(((Compound) byteCodeAppender).byteCodeAppenders);
                } else {
                    this.byteCodeAppenders.add(byteCodeAppender);
                }
            }
        }

        @Override
        public Size apply(MethodVisitor methodVisitor,
                          Implementation.Context implementationContext,
                          MethodDescription instrumentedMethod) {
            Size size = new Size(0, instrumentedMethod.getStackSize());
            for (ByteCodeAppender byteCodeAppender : byteCodeAppenders) {
                size = size.merge(byteCodeAppender.apply(methodVisitor, implementationContext, instrumentedMethod));
            }
            return size;
        }
    }

    /**
     * A simple byte code appender that only represents a given array of
     * {@link StackManipulation}s.
     */
    @EqualsAndHashCode
    class Simple implements ByteCodeAppender {

        /**
         * A compound stack manipulation to be applied for this byte code appender.
         */
        private final StackManipulation stackManipulation;

        /**
         * Creates a new simple byte code appender which represents the given stack manipulation.
         *
         * @param stackManipulation The stack manipulations to apply for this byte code appender in their application order.
         */
        public Simple(StackManipulation... stackManipulation) {
            this(Arrays.asList(stackManipulation));
        }

        /**
         * Creates a new simple byte code appender which represents the given stack manipulation.
         *
         * @param stackManipulations The stack manipulations to apply for this byte code appender in their application order.
         */
        public Simple(List<? extends StackManipulation> stackManipulations) {
            this.stackManipulation = new StackManipulation.Compound(stackManipulations);
        }

        @Override
        public Size apply(MethodVisitor methodVisitor,
                          Implementation.Context implementationContext,
                          MethodDescription instrumentedMethod) {
            return new Size(stackManipulation.apply(methodVisitor, implementationContext).getMaximalSize(), instrumentedMethod.getStackSize());
        }
    }
}
