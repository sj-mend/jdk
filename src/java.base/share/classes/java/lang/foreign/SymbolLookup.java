/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.foreign;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.SystemLookup;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * An object that may be used to look up symbols in one or more loaded libraries. A symbol lookup allows for searching
 * symbols by name, see {@link SymbolLookup#lookup(String)}. A library symbol is modelled as a zero-length {@linkplain MemorySegment memory segment};
 * it can be used directly to create a {@linkplain Linker#downcallHandle(Addressable, FunctionDescriptor) downcall method handle},
 * or it can be {@linkplain MemorySegment#ofAddress(MemoryAddress, long, MemorySession) resized} accordingly, if it models
 * a <em>global variable</em> that needs to be dereferenced.
 * <p>
 * Clients can obtain a {@linkplain #loaderLookup() loader lookup},
 * which can be used to find symbols in libraries loaded by the current classloader (e.g. using {@link System#load(String)},
 * or {@link System#loadLibrary(String)}).
 * <p>
 * Alternatively, clients can search symbols in the standard libraries associated with a {@link Linker} instance.
 * The set of symbols available in a linker lookup is unspecified, as it depends on the platform and on the operating system.
 * <p>
 * Finally, clients can load a library and obtain a {@linkplain #libraryLookup(Path, MemorySession) library lookup} which can be used
 * to find symbols in that library. A library lookup is associated with a {@linkplain  MemorySession memory session},
 * and the library it refers to is unloaded when the session is {@linkplain MemorySession#close() closed}.
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
@FunctionalInterface
public interface SymbolLookup {

    /**
     * Looks up a symbol with the given name in this lookup.
     *
     * @param name the symbol name.
     * @return the lookup symbol (if any).
     */
    Optional<MemorySegment> lookup(String name);

    /**
     * Returns a symbol lookup suitable to find symbols in shared libraries associated with the caller's classloader.
     * The returned lookup returns symbols backed by a non-closeable, shared scope which keeps the caller's classloader
     * <a href="../../../java/lang/ref/package.html#reachability">reachable</a>.
     *
     * @return a symbol lookup suitable to find symbols in libraries loaded by the caller's classloader.
     * @see System#load(String)
     * @see System#loadLibrary(String)
     */
    @CallerSensitive
    static SymbolLookup loaderLookup() {
        Class<?> caller = Reflection.getCallerClass();
        ClassLoader loader = Objects.requireNonNull(caller.getClassLoader());
        MemorySessionImpl loaderSession = MemorySessionImpl.heapSession(loader);
        return name -> {
            Objects.requireNonNull(name);
            JavaLangAccess javaLangAccess = SharedSecrets.getJavaLangAccess();
            MemoryAddress addr = MemoryAddress.ofLong(javaLangAccess.findNative(loader, name));
            return addr == MemoryAddress.NULL
                    ? Optional.empty() :
                    Optional.of(MemorySegment.ofAddress(addr, 0L, loaderSession));
        };
    }

    /**
     * Loads a library with the given name and creates a symbol lookup suitable to find symbols in that library.
     * The library will be unloaded when the provided memory session is {@linkplain MemorySession#close() closed}.
     * @apiNote The process of resolving a library name is platform-specific. For instance, on POSIX
     * systems, the library name is resolved according to the specification of the {@code dlopen} function.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @param name the name of the library in which symbols should be looked up.
     * @param session the memory session which controls the library lifecycle.
     * @return a new symbol lookup suitable to find symbols in a library with the given name.
     * @throws IllegalArgumentException if {@code name} does not identify a valid library.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static SymbolLookup libraryLookup(String name, MemorySession session) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), SymbolLookup.class, "libraryLookup");
        return libraryLookup(name, RawNativeLibraries::load, session);
    }

    /**
     * Loads a library with the given path and creates a symbol lookup suitable to find symbols in that library.
     * The library will be unloaded when the provided memory session is {@linkplain MemorySession#close() closed}.
     * <p>
     * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
     * Restricted methods are unsafe, and, if used incorrectly, their use might crash
     * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
     * restricted methods, and use safe and supported functionalities, where possible.
     * @param path the path of the library in which symbols should be looked up.
     * @param session the memory session which controls the library lifecycle.
     * @return a new symbol lookup suitable to find symbols in a library with the given path.
     * @throws IllegalArgumentException if {@code path} does not point to a valid library.
     * @throws IllegalCallerException if access to this method occurs from a module {@code M} and the command line option
     * {@code --enable-native-access} is either absent, or does not mention the module name {@code M}, or
     * {@code ALL-UNNAMED} in case {@code M} is an unnamed module.
     */
    @CallerSensitive
    static SymbolLookup libraryLookup(Path path, MemorySession session) {
        Reflection.ensureNativeAccess(Reflection.getCallerClass(), SymbolLookup.class, "libraryLookup");
        return libraryLookup(path, RawNativeLibraries::load, session);
    }

    private static <Z> SymbolLookup libraryLookup(Z libDesc, BiFunction<RawNativeLibraries, Z, NativeLibrary> loadLibraryFunc, MemorySession session) {
        Objects.requireNonNull(libDesc);
        Objects.requireNonNull(session);
        // attempt to load native library from path or name
        RawNativeLibraries nativeLibraries = RawNativeLibraries.newInstance(MethodHandles.lookup());
        NativeLibrary library = loadLibraryFunc.apply(nativeLibraries, libDesc);
        if (library == null) {
            throw new IllegalArgumentException("Cannot open library: " + libDesc);
        }
        // register hook to unload library when session is closed
        MemorySessionImpl.toSessionImpl(session).addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                nativeLibraries.unload(library);
            }
        });
        return name -> {
            Objects.requireNonNull(name);
            MemoryAddress addr = MemoryAddress.ofLong(library.find(name));
            return addr == MemoryAddress.NULL
                    ? Optional.empty() :
                    Optional.of(MemorySegment.ofAddress(addr, 0L, session));
        };
    }
}
