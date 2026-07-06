package com.sk.macpad.platform;

import javax.swing.SwingUtilities;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.DoubleConsumer;

public final class MagnifyGesture {

    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;
    private static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE;
    private static final java.lang.foreign.AddressLayout PTR = ValueLayout.ADDRESS;

    private static volatile DoubleConsumer callback;
    private static MethodHandle msgSend;
    private static MethodHandle msgSendD;
    private static MethodHandle msgSendSetD;
    private static MethodHandle msgSendLong;
    private static MethodHandle msgSendIdx;
    private static MethodHandle msgSendInit;
    private static MethodHandle msgSendAddGr;
    private static MemorySegment selMagnification;
    private static MemorySegment selSetMagnification;

    private MagnifyGesture() {}

    public static synchronized void install(DoubleConsumer onMagnify) {
        callback = onMagnify;
        try {
            Linker linker = Linker.nativeLinker();
            Arena arena = Arena.global();
            SymbolLookup objc = SymbolLookup.libraryLookup("libobjc.A.dylib", arena);

            MethodHandle getClass = linker.downcallHandle(objc.find("objc_getClass").orElseThrow(),
                    FunctionDescriptor.of(PTR, PTR));
            MethodHandle sel = linker.downcallHandle(objc.find("sel_registerName").orElseThrow(),
                    FunctionDescriptor.of(PTR, PTR));
            MethodHandle allocClass = linker.downcallHandle(objc.find("objc_allocateClassPair").orElseThrow(),
                    FunctionDescriptor.of(PTR, PTR, PTR, LONG));
            MethodHandle registerClass = linker.downcallHandle(objc.find("objc_registerClassPair").orElseThrow(),
                    FunctionDescriptor.ofVoid(PTR));
            MethodHandle addMethod = linker.downcallHandle(objc.find("class_addMethod").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, PTR, PTR, PTR, PTR));

            MemorySegment send = objc.find("objc_msgSend").orElseThrow();
            msgSend = linker.downcallHandle(send, FunctionDescriptor.of(PTR, PTR, PTR));
            msgSendD = linker.downcallHandle(send, FunctionDescriptor.of(DOUBLE, PTR, PTR));
            msgSendSetD = linker.downcallHandle(send, FunctionDescriptor.ofVoid(PTR, PTR, DOUBLE));
            msgSendLong = linker.downcallHandle(send, FunctionDescriptor.of(LONG, PTR, PTR));
            msgSendIdx = linker.downcallHandle(send, FunctionDescriptor.of(PTR, PTR, PTR, LONG));
            msgSendInit = linker.downcallHandle(send, FunctionDescriptor.of(PTR, PTR, PTR, PTR, PTR));
            msgSendAddGr = linker.downcallHandle(send, FunctionDescriptor.of(PTR, PTR, PTR, PTR));

            selMagnification = sel(sel, arena, "magnification");
            selSetMagnification = sel(sel, arena, "setMagnification:");
            MemorySegment selAlloc = sel(sel, arena, "alloc");
            MemorySegment selInit = sel(sel, arena, "init");
            MemorySegment selShared = sel(sel, arena, "sharedApplication");
            MemorySegment selWindows = sel(sel, arena, "windows");
            MemorySegment selCount = sel(sel, arena, "count");
            MemorySegment selObjectAt = sel(sel, arena, "objectAtIndex:");
            MemorySegment selContentView = sel(sel, arena, "contentView");
            MemorySegment selAddGesture = sel(sel, arena, "addGestureRecognizer:");
            MemorySegment selInitTargetAction = sel(sel, arena, "initWithTarget:action:");
            MemorySegment selHandle = sel(sel, arena, "handleMagnify:");

            MethodHandle upcall = MethodHandles.lookup().findStatic(MagnifyGesture.class, "onMagnifyNative",
                    MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment imp = linker.upcallStub(upcall, FunctionDescriptor.ofVoid(PTR, PTR, PTR), arena);

            MemorySegment nsObject = (MemorySegment) getClass.invoke(arena.allocateFrom("NSObject"));
            MemorySegment targetClass =
                    (MemorySegment) allocClass.invoke(nsObject, arena.allocateFrom("MacPadMagnifyTarget"), 0L);
            addMethod.invoke(targetClass, selHandle, imp, arena.allocateFrom("v@:@"));
            registerClass.invoke(targetClass);
            MemorySegment target = (MemorySegment) msgSend.invoke(targetClass, selAlloc);
            target = (MemorySegment) msgSend.invoke(target, selInit);

            MemorySegment nsApp =
                    (MemorySegment) msgSend.invoke(getClass.invoke(arena.allocateFrom("NSApplication")), selShared);
            MemorySegment windows = (MemorySegment) msgSend.invoke(nsApp, selWindows);
            long count = (long) msgSendLong.invoke(windows, selCount);
            MemorySegment recognizerClass =
                    (MemorySegment) getClass.invoke(arena.allocateFrom("NSMagnificationGestureRecognizer"));
            for (long i = 0; i < count; i++) {
                MemorySegment window = (MemorySegment) msgSendIdx.invoke(windows, selObjectAt, i);
                MemorySegment view = (MemorySegment) msgSend.invoke(window, selContentView);
                if (view.address() == 0) continue;
                MemorySegment recognizer = (MemorySegment) msgSend.invoke(recognizerClass, selAlloc);
                recognizer = (MemorySegment) msgSendInit.invoke(recognizer, selInitTargetAction, target, selHandle);
                msgSendAddGr.invoke(view, selAddGesture, recognizer);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private static MemorySegment sel(MethodHandle selRegisterName, Arena arena, String name) throws Throwable {
        return (MemorySegment) selRegisterName.invoke(arena.allocateFrom(name));
    }

    @SuppressWarnings("unused")
    private static void onMagnifyNative(MemorySegment self, MemorySegment cmd, MemorySegment recognizer) {
        try {
            double delta = (double) msgSendD.invoke(recognizer, selMagnification);
            msgSendSetD.invoke(recognizer, selSetMagnification, 0.0);
            DoubleConsumer c = callback;
            if (c != null) SwingUtilities.invokeLater(() -> c.accept(delta));
        } catch (Throwable t) {
            // ignore
        }
    }
}
