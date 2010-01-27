/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2006 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package _templates.javolution.context;

import _templates.java.lang.ThreadLocal;
import _templates.javolution.lang.Configurable;
import _templates.javolution.util.FastMap;
import _templates.javolution.util.FastTable;

/**
 * <p> This class represents a stack {@link AllocatorContext allocator context};
 *     (using thread-local pools or RTSJ <code>ScopedMemory</code>).</p>
 *       
 * <p> Stacks allocations reduce heap memory allocation and often result in 
 *     faster execution time for almost all objects but the smallest one.</p>
 *     
 * <p> Stack allocated objects should never be assigned to static members 
 *     (see {@link ImmortalContext}). Also, methods entering/exiting stack 
 *     contexts should ensure that stack allocated objects do not escape from
 *     their context scope. If necessary, stack objects can be exported using 
 *     {@link #outerExecute} or {@link #outerCopy}:[code]
 *     public class LargeInteger implements ValueType, Realtime {
 *         public LargeInteger sqrt() {
 *             StackContext.enter(); 
 *             try { 
 *                 LargeInteger result = ZERO;
 *                 LargeInteger k = this.shiftRight(this.bitLength() / 2)); // First approximation.
 *                 while (true) { // Newton Iteration.
 *                     result = (k.plus(this.divide(k))).shiftRight(1);
 *                     if (result.equals(k)) return StackContext.outerCopy(result); // Exports result.
 *                     k = result;
 *                 }
 *             } finally { 
 *                 StackContext.exit(); 
 *             }
 *         }
 *     }[/code]</p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 5.2, August 19, 2007
 */
public abstract class StackContext extends AllocatorContext {

    /**
     * Holds the default implementation. This implementation uses thread-local
     * pools. RTSJ alternative implementations could use 
     * <code>ScopedMemory</code> for their stack allocations.
     * Users may also disable stack allocation by providing a class allocating
     * on the heap.
     */
    public static final Configurable/*<Class<? extends StackContext>>*/ DEFAULT 
            = new Configurable(Default.class) {};

    /**
     * Enters the {@link #DEFAULT} stack context.
     */
    public static void enter() {
        Context.enter((Class) DEFAULT.get());
    }

    /**
     * Enters a stack context only if the specified condition is verified.
     *
     * @param condition <code>true</code> to enter a stack context;
     *                  <code>false</code> otherwise.
     */
    public static void enter(boolean condition) {
        if (condition) {
            StackContext.enter();
        }
    }

    /**
     * Exits the current stack context.
     * 
     * @throws ClassCastException if the context is not a stack context.
     */
    public static void exit() {
        Context.exit(StackContext.class);
    }

    /**
     * Exits a stack context only if the specified condition is verified.
     *
     * @param condition <code>true</code> to exit a stack context;
     *                  <code>false</code> otherwise.
     */
    public static void exit(boolean condition) {
        if (condition) {
            StackContext.exit();
        }
    }

    /**
     * Default implementation. 
     */
    private static final class Default extends StackContext {

        private final ThreadLocal _factoryToAllocator = new ThreadLocal() {

            protected Object initialValue() {
                return new FastMap();
            }
        };
        private final ThreadLocal _activeAllocators = new ThreadLocal() {

            protected Object initialValue() {
                return new FastTable();
            }
        };
        // All allocators which have been used by the owner  
        // (no synchronization required).
        private final FastTable _ownerUsedAllocators = new FastTable();
        // All allocators which have been used by the concurrent threads 
        // (synchronization required).
        private final FastTable _nonOwnerUsedAllocators = new FastTable();

        protected void deactivate() {
            FastTable allocators = (FastTable) _activeAllocators.get();
            for (int i = 0, n = allocators.size(); i < n;) {
                ((Allocator) allocators.get(i++)).user = null;
            }
            allocators.clear();
        }

        protected Allocator getAllocator(ObjectFactory factory) {
            FastMap factoryToAllocator = (FastMap) _factoryToAllocator.get();
            StackAllocator allocator = (StackAllocator) factoryToAllocator.get(factory);
            if (allocator == null) {
                allocator = new StackAllocator(factory);
                factoryToAllocator.put(factory, allocator);
            }
            if (allocator.user == null) { // Activate.
                allocator.user = Thread.currentThread();
                FastTable activeAllocators = (FastTable) _activeAllocators.get();
                activeAllocators.add(allocator);
            }
            if (!allocator._inUse) { // Add to lists of allocators used.
                allocator._inUse = true;
                if (Thread.currentThread() == getOwner())
                    _ownerUsedAllocators.add(allocator);
                else
                    synchronized (_nonOwnerUsedAllocators) {
                        _nonOwnerUsedAllocators.add(allocator);
                    }
            }
            return allocator;
        }

        protected void enterAction() {
            getOuter().getAllocatorContext().deactivate();
        }

        protected void exitAction() {
            this.deactivate();

            // Resets all allocators used.
            for (int i = 0; i < _ownerUsedAllocators.size(); i++) {
                StackAllocator allocator = (StackAllocator) _ownerUsedAllocators.get(i);
                allocator.reset();
            }
            _ownerUsedAllocators.clear();
            for (int i = 0; i < _nonOwnerUsedAllocators.size(); i++) {
                StackAllocator allocator = (StackAllocator) _nonOwnerUsedAllocators.get(i);
                allocator.reset();
            }
            _nonOwnerUsedAllocators.clear();
        }
    }

    // Holds stack allocator implementation.
    private static final class StackAllocator extends Allocator {

        private final ObjectFactory _factory;
        private boolean _inUse;
        private int _queueLimit;

        public StackAllocator(ObjectFactory factory) {
            this._factory = factory;
        }

        protected Object allocate() {
            if (_queueLimit >= queue.length)
                resize();
            Object obj = _factory.create();
            queue[_queueLimit++] = obj;
            return obj;
        }

        protected void recycle(Object object) {
            if (_factory.doCleanup())
                _factory.cleanup(object);
            for (int i = queueSize; i < _queueLimit; i++) {
                if (queue[i] == object) { // Found it.
                    queue[i] = queue[queueSize];
                    queue[queueSize++] = object;
                    return;
                }
            }
            throw new _templates.java.lang.UnsupportedOperationException(
                    "Cannot recycle to the stack an object " +
                    "which has not been allocated from the stack");
        }

        protected void reset() {
            _inUse = false;
            while (_factory.doCleanup() && (queueSize != _queueLimit)) {
                Object obj = queue[queueSize++];
                _factory.cleanup(obj);
            }
            queueSize = _queueLimit;
        }

        public String toString() {
            return "Stack allocator for " + _factory.getClass();
        }
    }

    // Allows instances of private classes to be factory produced. 
    static {
        ObjectFactory.setInstance(new ObjectFactory() {

            protected Object create() {
                return new Default();
            }
        }, Default.class);
    }
}
