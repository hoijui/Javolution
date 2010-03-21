/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2006 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package _templates.javolution.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import _templates.java.lang.UnsupportedOperationException;
import _templates.java.nio.ByteBuffer;
import _templates.java.nio.ByteOrder;
import _templates.java.util.List;
import _templates.javolution.lang.Configurable;
import _templates.javolution.lang.Enum;
import _templates.javolution.lang.MathLib;
import _templates.javolution.lang.Reflection;
import _templates.javolution.text.TextBuilder;

/**
 * <p> This class represents a <code>C/C++ struct</code>; it confers
 *     interoperability between Java classes and C/C++ struct.</p>
 * <p> Unlike <code>C/C++</code>, the storage layout of Java objects is not
 *     determined by the compiler. The layout of objects in memory is deferred
 *     to run time and determined by the interpreter (or just-in-time compiler).
 *     This approach allows for dynamic loading and binding; but also makes
 *     interfacing with <code>C/C++</code> code difficult. Hence, this class for
 *     which the memory layout is defined by the initialization order of the
 *     {@link Struct}'s {@link Member members} and follows the same alignment
 *      rules as <code>C/C++ structs</code>.</p>
 * <p> This class (as well as the {@link Union} sub-class) facilitates:
 *     <ul>
 *     <li> Memory sharing between Java applications and native libraries.</li>
 *     <li> Direct encoding/decoding of streams for which the structure
 *          is defined by legacy C/C++ code.</li>
 *     <li> Serialization/deserialization of Java objects (complete control,
 *          e.g. no class header)</li>
 *     <li> Mapping of Java objects to physical addresses (with JNI).</li>
 *     </ul></p>
 * <p> Because of its one-to-one mapping, it is relatively easy to convert C
 *     header files (e.g. OpenGL bindings) to Java {@link Struct}/{@link Union}
 *     using simple text macros. Here is an example of C struct:<code><pre>
 *     struct Date {
 *         unsigned short year;
 *         unsigned byte month;
 *         unsigned byte day;
 *     };
 *     struct Student {
 *         char        name[64];
 *         struct Date birth;
 *         float       grades[10];
 *         Student*    next;
 *     };</pre></code>
 *     and here is the Java equivalent using this class:[code]
 *     public static class Date extends Struct {
 *         public final Unsigned16 year = new Unsigned16();
 *         public final Unsigned8 month = new Unsigned8();
 *         public final Unsigned8 day   = new Unsigned8();
 *     }
 *     public static class Student extends Struct {
 *         public final UTF8String  name   = new UTF8String(64);
 *         public final Date        birth  = inner(new Date());
 *         public final Float32[]   grades = array(new Float32[10]);
 *         public final Reference32<Student> next =  new Reference32<Student>();
 *     }[/code]
 *     Struct's members are directly accessible:[code]
 *     Student student = new Student();
 *     student.name.set("John Doe"); // Null terminated (C compatible)
 *     int age = 2003 - student.birth.year.get();
 *     student.grades[2].set(12.5f);
 *     student = student.next.get();[/code]</p>
 * <p> Applications may also work with the raw {@link #getByteBuffer() bytes}
 *     directly. The following illustrate how {@link Struct} can be used to
 *     decode/encode UDP messages directly:[code]
 *     class UDPMessage extends Struct {
 *          Unsigned16 xxx = new Unsigned16();
 *          ...
 *     }
 *     public void run() {
 *         byte[] bytes = new byte[1024];
 *         DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
 *         UDPMessage message = new UDPMessage();
 *         message.setByteBuffer(ByteBuffer.wrap(bytes), 0);
 *         // packet and message are now two different views of the same data.
 *         while (isListening) {
 *             multicastSocket.receive(packet);
 *             int xxx = message.xxx.get();
 *             ... // Process message fields directly.
 *         }
 *     }[/code]</p>
 * <p> It is relatively easy to map instances of this class to any physical
 *     address using
 *     <a href="http://java.sun.com/docs/books/tutorial/native1.1/index.html">
 *     JNI</a>. Here is an example:[code]
 *     import java.nio.ByteBuffer;
 *     class Clock extends Struct { // Hardware clock mapped to memory.
 *         Unsigned16 seconds  = new Unsigned16(5); // unsigned short seconds:5
 *         Unsigned16 minutes  = new Unsigned16(5); // unsigned short minutes:5
 *         Unsigned16 hours    = new Unsigned16(4); // unsigned short hours:4
 *         Clock() {
 *             setByteBuffer(Clock.nativeBuffer(), 0);
 *         }
 *         private static native ByteBuffer nativeBuffer();
 *     }[/code]
 *     Below is the <code>nativeBuffer()</code> implementation
 *     (<code>Clock.c</code>):[code]
 *     #include <jni.h>
 *     #include "Clock.h" // Generated using javah
 *     JNIEXPORT jobject JNICALL Java_Clock_nativeBuffer (JNIEnv *env, jclass) {
 *         return (*env)->NewDirectByteBuffer(env, clock_address, buffer_size)
 *     }[/code]</p>
 * <p> Bit-fields are supported (see <code>Clock</code> example above).
 *     Bit-fields allocation order is defined by the Struct {@link #byteOrder}
 *     return value (leftmost bit to rightmost bit if
 *     <code>BIG_ENDIAN</code> and rightmost bit to leftmost bit if
 *      <code>LITTLE_ENDIAN</code>).
 *     Unless the Struct {@link #isPacked packing} directive is overridden,
 *     bit-fields cannot straddle the storage-unit boundary as defined by their
 *     base type (padding is inserted at the end of the first bit-field
 *     and the second bit-field is put into the next storage unit).</p>
 * <p> Finally, it is possible to change the {@link #setByteBuffer ByteBuffer}
 *     and/or the Struct {@link #setByteBufferPosition position} in its
 *     <code>ByteBuffer</code> to allow for a single {@link Struct} object to
 *     encode/decode multiple memory mapped instances.</p>
 *
 * <p><i>Note: Because Struct/Union are basically wrappers around
 *             <code>java.nio.ByteBuffer</code>, tutorials/usages for the
 *             Java NIO package are directly applicable to Struct/Union.</i></p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @version 5.4, November 2, 2009
 */
public class Struct {

    /**
     * Configurable holding the maximum alignment in bytes
     * (default <code>4</code>). Should be a value greater or equal to 1.
     */
    public static final Configurable/*<Integer>*/ MAXIMUM_ALIGNMENT = new Configurable(new Integer(4)) {
    };

    /**
     * Holds the outer struct if any.
     */
    Struct _outer;

    /**
     * Holds the byte buffer backing the struct (top struct).
     */
    ByteBuffer _byteBuffer;

    /**
     * Holds the offset of this struct relative to the outer struct or
     * to the byte buffer if there is no outer.
     */
    int _outerOffset;

    /**
     * Holds the number of bits currently used (for size calculation).
     */
    int _bitsUsed;

    /**
     * Holds this struct alignment in bytes (largest alignment of its members).
     */
    int _alignment = 1;

    /**
     * Holds the current bit index position (during construction).
     */
    int _bitIndex;

    /**
     * Indicates if the index has to be reset for each new field (
     * <code>true</code> only for Union subclasses).
     */
    boolean _resetIndex;

    /**
     * Holds bytes array for Stream I/O when byteBuffer has no intrinsic array.
     */
    byte[] _bytes;

    /**
     * Default constructor.
     */
    public Struct() {
        _resetIndex = isUnion();
    }

    /**
     * Returns the size in bytes of this struct. The size includes
     * tail padding to satisfy the struct alignment requirement
     * (defined by the largest alignment of its {@link Member members}).
     *
     * @return the C/C++ <code>sizeof(this)</code>.
     */
    public final int size() {
        int nbrOfBytes = (_bitsUsed + 7) >> 3;
        return ((nbrOfBytes % _alignment) == 0) ? nbrOfBytes : // Already aligned or packed.
                nbrOfBytes + _alignment - (nbrOfBytes % _alignment); // Tail padding.
    }

    /**
     * Returns the outer of this struct or <code>null</code> if this struct
     * is not an inner struct.
     *
     * @return the outer struct or <code>null</code>.
     */
    public Struct outer() {
        return _outer;
    }

    /**
     * Returns the byte buffer for this struct. This method will allocate
     * a new <b>direct</b> buffer if none has been set.
     *
     * <p> Changes to the buffer's content are visible in this struct,
     *     and vice versa.</p>
     * <p> The buffer of an inner struct is the same as its parent struct.</p>
     * <p> The bit position of a {@link Struct.Member struct's member} within
     *     the byte buffer is given by {@link Struct.Member#bitOffset
     *     member.bitOffset()}</p>
     * <p> If no byte buffer has been {@link Struct#setByteBuffer set},
     *     a direct buffer is allocated with a capacity equals to this
     *     struct's {@link Struct#size() size}.</p>
     *
     * @return the current byte buffer or a new direct buffer if none set.
     * @see #setByteBuffer
     */
    public final ByteBuffer getByteBuffer() {
        if (_outer != null) {
            return _outer.getByteBuffer();
        }
        return (_byteBuffer != null) ? _byteBuffer : newBuffer();
    }

    private synchronized ByteBuffer newBuffer() {
        if (_byteBuffer != null) {
            return _byteBuffer; // Synchronized check.
        }
        ByteBuffer bf = ByteBuffer.allocateDirect(size());
        bf.order(byteOrder());
        setByteBuffer(bf, 0);
        return _byteBuffer;
    }

    /**
     * Sets the current byte buffer for this struct.
     * The specified byte buffer can be mapped to memory for direct memory
     * access or can wrap a shared byte array for I/O purpose
     * (e.g. <code>DatagramPacket</code>).
     * The capacity of the specified byte buffer should be at least the
     * {@link Struct#size() size} of this struct plus the offset position.
     *
     * @param byteBuffer the new byte buffer.
     * @param position the position of this struct in the specified byte buffer.
     * @return <code>this</code>
     * @throws IllegalArgumentException if the specified byteBuffer has a
     *         different byte order than this struct.
     * @throws UnsupportedOperationException if this struct is an inner struct.
     * @see #byteOrder()
     */
    public final Struct setByteBuffer(ByteBuffer byteBuffer, int position) {
        if (byteBuffer.order() != byteOrder()) {
            throw new IllegalArgumentException(
                    "The byte order of the specified byte buffer" + " is different from this struct byte order");
        }
        if (_outer != null) {
            throw new UnsupportedOperationException(
                    "Inner struct byte buffer is inherited from outer");
        }
        _byteBuffer = byteBuffer;
        _outerOffset = position;
        return this;
    }

    /**
     * Sets the byte position of this struct within its byte buffer.
     *
     * @param position the position of this struct in its byte buffer.
     * @return <code>this</code>
     * @throws UnsupportedOperationException if this struct is an inner struct.
     */
    public final Struct setByteBufferPosition(int position) {
        return setByteBuffer(this.getByteBuffer(), position);
    }

    /**
     * Returns the absolute byte position of this struct within its associated
     * {@link #getByteBuffer byte buffer}.
     *
     * @return the absolute position of this struct (can be an inner struct)
     *         in the byte buffer.
     */
    public final int getByteBufferPosition() {
        return (_outer != null) ? _outer.getByteBufferPosition() + _outerOffset
                : _outerOffset;
    }

    /**
     * Reads this struct from the specified input stream
     * (convenience method when using Stream I/O). For better performance,
     * use of Block I/O (e.g. <code>java.nio.channels.*</code>) is recommended.
     *
     * @param in the input stream being read from.
     * @return the number of bytes read (typically the {@link #size() size}
     *         of this struct.
     * @throws IOException if an I/O error occurs.
     */
    public int read(InputStream in) throws IOException {
        ByteBuffer buffer = getByteBuffer();
        if (buffer.hasArray()) {
            int offset = buffer.arrayOffset() + getByteBufferPosition();
            return in.read(buffer.array(), offset, size());
        } else {
            synchronized (buffer) {
                if (_bytes == null) {
                    _bytes = new byte[size()];
                }
                int bytesRead = in.read(_bytes);
                buffer.position(getByteBufferPosition());
                buffer.put(_bytes);
                return bytesRead;
            }
        }
    }

    /**
     * Writes this struct to the specified output stream
     * (convenience method when using Stream I/O). For better performance,
     * use of Block I/O (e.g. <code>java.nio.channels.*</code>) is recommended.
     *
     * @param out the output stream to write to.
     * @throws IOException if an I/O error occurs.
     */
    public void write(OutputStream out) throws IOException {
        ByteBuffer buffer = getByteBuffer();
        if (buffer.hasArray()) {
            int offset = buffer.arrayOffset() + getByteBufferPosition();
            out.write(buffer.array(), offset, size());
        } else {
            synchronized (buffer) {
                if (_bytes == null) {
                    _bytes = new byte[size()];
                }
                buffer.position(getByteBufferPosition());
                buffer.get(_bytes);
                out.write(_bytes);
            }
        }
    }

    /**
     * Returns this struct address. This method allows for structs
     * to be referenced (e.g. pointer) from other structs.
     *
     * @return the struct memory address.
     * @throws UnsupportedOperationException if the struct buffer is not
     *         a direct buffer.
     * @see    Reference32
     * @see    Reference64
     */
    public final long address() {
        ByteBuffer thisBuffer = this.getByteBuffer();
        if (ADDRESS_METHOD != null) {
            Long start = (Long) ADDRESS_METHOD.invoke(thisBuffer);
            return start.longValue() + getByteBufferPosition();
        } else {
            throw new UnsupportedOperationException(
                    "Operation not supported for " + thisBuffer.getClass());
        }
    }
    private static final Reflection.Method ADDRESS_METHOD = Reflection.getInstance().getMethod("sun.nio.ch.DirectBuffer.address()");

    /**
     * Returns the <code>String</code> representation of this struct
     * in the form of its constituing bytes (hexadecimal). For example:[code]
     *     public static class Student extends Struct {
     *         Utf8String name  = new Utf8String(16);
     *         Unsigned16 year  = new Unsigned16();
     *         Float32    grade = new Float32();
     *     }
     *     Student student = new Student();
     *     student.name.set("John Doe");
     *     student.year.set(2003);
     *     student.grade.set(12.5f);
     *     System.out.println(student);
     *
     *     4A 6F 68 6E 20 44 6F 65 00 00 00 00 00 00 00 00
     *     07 D3 00 00 41 48 00 00[/code]
     *
     * @return a hexadecimal representation of the bytes content for this
     *         struct.
     */
    public String toString() {
        final int size = size();
        StringBuffer sb = new StringBuffer(size * 3);
        final ByteBuffer buffer = getByteBuffer();
        final int start = getByteBufferPosition();
        for (int i = 0; i < size; i++) {
            int b = buffer.get(start + i) & 0xFF;
            sb.append(HEXA[b >> 4]);
            sb.append(HEXA[b & 0xF]);
            sb.append(((i & 0xF) == 0xF) ? '\n' : ' ');
        }
        return sb.toString();
    }
    private static final char[] HEXA = {'0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    ///////////////////
    // CONFIGURATION //
    ///////////////////
    /**
     * Indicates if this struct's members are mapped to the same location
     * in memory (default <code>false</code>). This method is useful for
     * applications extending {@link Struct} with new member types in order to
     * create unions from these new structs. For example:[code]
     * public abstract class FortranStruct extends Struct {
     *     public class FortranString extends Member {...}
     *     protected FortranString[] array(FortranString[] array, int stringLength) { ... }
     * }
     * public abstract class FortranUnion extends FortranStruct {
     *     // Inherits new members and methods.
     *     public final isUnion() {
     *         return true;
     *     }
     * }[/code]
     *
     * @return <code>true</code> if this struct's members are mapped to
     *         to the same location in memory; <code>false</code>
     *         otherwise.
     * @see Union
     */
    public boolean isUnion() {
        return false;
    }

    /**
     * Returns the byte order for this struct (configurable).
     * The byte order is inherited by inner structs. Sub-classes may change
     * the byte order by overriding this method. For example:[code]
     * public class TopStruct extends Struct {
     *     ... // Members initialization.
     *     public ByteOrder byteOrder() {
     *         // TopStruct and its inner structs use hardware byte order.
     *         return ByteOrder.nativeOrder();
     *    }
     * }}[/code]</p></p>
     *
     * @return the byte order when reading/writing multibyte values
     *         (default: network byte order, <code>BIG_ENDIAN</code>).
     */
    public ByteOrder byteOrder() {
        return (_outer != null) ? _outer.byteOrder() : ByteOrder.BIG_ENDIAN;
    }

    /**
     * Indicates if this struct is packed (configurable).
     * By default, {@link Member members} of a struct are aligned on the
     * boundary corresponding to the member base type; padding is performed
     * if necessary. This directive is <b>not</b> inherited by inner structs.
     * Sub-classes may change the packing directive by overriding this method.
     * For example:[code]
     * public class MyStruct extends Struct {
     *     ... // Members initialization.
     *     public boolean isPacked() {
     *         return true; // MyStruct is packed.
     *     }
     * }}[/code]
     *
     * @return <code>true</code> if alignment requirements are ignored.
     *         <code>false</code> otherwise (default).
     */
    public boolean isPacked() {
        return false;
    }

    /**
     * Defines the specified struct as inner of this struct.
     *
     * @param struct the inner struct.
     * @return the specified struct.
     * @throws IllegalArgumentException if the specified struct is already
     *         an inner struct.
     */
    protected/* <S extends Struct> S*/ Struct inner(/*S*/Struct struct) {
        if (struct._outer != null) {
            throw new IllegalArgumentException(
                    "struct: Already an inner struct");
        }
        struct._outer = this;
        final int bitSize = struct.size() << 3;
        int bitOffset = updateIndexes(struct._alignment, bitSize);
        struct._outerOffset = bitOffset >> 3; // Always byte aligned.
        return (/*S*/Struct) struct;
    }

    /**
     * Defines the specified array of structs as inner structs.
     * The array is populated if necessary using the struct component
     * default constructor (which must be public).
     *
     * @param structs the struct array.
     * @return the specified struct array.
     * @throws IllegalArgumentException if the specified array contains
     *         inner structs.
     */
    protected/* <S extends Struct> S*/ Struct[] array(/*S*/Struct[] structs) {
        Class structClass = null;
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _bitIndex = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < structs.length;) {
            /*S*/Struct struct = structs[i];
            if (struct == null) {
                try {
                    if (structClass == null) {
                        String arrayName = structs.getClass().getName();
                        String structName = arrayName.substring(2, arrayName.length() - 1);
                        structClass = Reflection.getInstance().getClass(structName);
                        if (structClass == null) {
                            throw new IllegalArgumentException("Struct class: " + structName + " not found");
                        }
                    }
                    struct = (/*S*/Struct) structClass.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
            structs[i++] = inner(struct);
        }
        _resetIndex = resetIndexSaved;
        return (/*S*/Struct[]) structs;
    }

    /**
     * Defines the specified two-dimensional array of structs as inner
     * structs. The array is populated if necessary using the struct component
     * default constructor (which must be public).
     *
     * @param structs the two dimensional struct array.
     * @return the specified struct array.
     * @throws IllegalArgumentException if the specified array contains
     *         inner structs.
     */
    protected/* <S extends Struct> S*/ Struct[][] array(
            /*S*/Struct[][] structs) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _bitIndex = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < structs.length; i++) {
            array(structs[i]);
        }
        _resetIndex = resetIndexSaved;
        return (/*S*/Struct[][]) structs;
    }

    /**
     * Defines the specified three dimensional array of structs as inner
     * structs. The array is populated if necessary using the struct component
     * default constructor (which must be public).
     *
     * @param structs the three dimensional struct array.
     * @return the specified struct array.
     * @throws IllegalArgumentException if the specified array contains
     *         inner structs.
     */
    protected/* <S extends Struct> S*/ Struct[][][] array(
            /*S*/Struct[][][] structs) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _bitIndex = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < structs.length; i++) {
            array(structs[i]);
        }
        _resetIndex = resetIndexSaved;
        return (/*S*/Struct[][][]) structs;
    }

    /**
     * Defines the specified array member. For predefined members,
     * the array is populated when empty; custom members should use
     * literal (populated) arrays.
     *
     * @param  arrayMember the array member.
     * @return the specified array member.
     * @throws UnsupportedOperationException if the specified array
     *         is empty and the member type is unknown.
     */
    protected/* <M extends Member> M*/ Member[] array(
            /*M*/Member[] arrayMember) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _bitIndex = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        if (BOOL.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Bool();
            }
        } else if (SIGNED_8.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Signed8();
            }
        } else if (UNSIGNED_8.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Unsigned8();
            }
        } else if (SIGNED_16.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Signed16();
            }
        } else if (UNSIGNED_16.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Unsigned16();
            }
        } else if (SIGNED_32.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Signed32();
            }
        } else if (UNSIGNED_32.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Unsigned32();
            }
        } else if (SIGNED_64.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Signed64();
            }
        } else if (FLOAT_32.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Float32();
            }
        } else if (FLOAT_64.isInstance(arrayMember)) {
            for (int i = 0; i < arrayMember.length;) {
                arrayMember[i++] = (/*M*/Member) this.new Float64();
            }
        } else {
            throw new UnsupportedOperationException(
                    "Cannot create member elements, the arrayMember should " + "contain the member instances instead of null");
        }
        _resetIndex = resetIndexSaved;
        return (/*M*/Member[]) arrayMember;
    }
    private static final Class BOOL = new Bool[0].getClass();

    private static final Class SIGNED_8 = new Signed8[0].getClass();

    private static final Class UNSIGNED_8 = new Unsigned8[0].getClass();

    private static final Class SIGNED_16 = new Signed16[0].getClass();

    private static final Class UNSIGNED_16 = new Unsigned16[0].getClass();

    private static final Class SIGNED_32 = new Signed32[0].getClass();

    private static final Class UNSIGNED_32 = new Unsigned32[0].getClass();

    private static final Class SIGNED_64 = new Signed64[0].getClass();

    private static final Class FLOAT_32 = new Float32[0].getClass();

    private static final Class FLOAT_64 = new Float64[0].getClass();

    /**
     * Defines the specified two-dimensional array member. For predefined
     * members, the array is populated when empty; custom members should use
     * literal (populated) arrays.
     *
     * @param  arrayMember the two-dimensional array member.
     * @return the specified array member.
     * @throws UnsupportedOperationException if the specified array
     *         is empty and the member type is unknown.
     */
    protected/* <M extends Member> M*/ Member[][] array(
            /*M*/Member[][] arrayMember) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _bitIndex = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < arrayMember.length; i++) {
            array(arrayMember[i]);
        }
        _resetIndex = resetIndexSaved;
        return (/*M*/Member[][]) arrayMember;
    }

    /**
     * Defines the specified three-dimensional array member. For predefined
     * members, the array is populated when empty; custom members should use
     * literal (populated) arrays.
     *
     * @param  arrayMember the three-dimensional array member.
     * @return the specified array member.
     * @throws UnsupportedOperationException if the specified array
     *         is empty and the member type is unknown.
     */
    protected/* <M extends Member> M*/ Member[][][] array(
            /*M*/Member[][][] arrayMember) {
        boolean resetIndexSaved = _resetIndex;
        if (_resetIndex) {
            _bitIndex = 0;
            _resetIndex = false; // Ensures the array elements are sequential.
        }
        for (int i = 0; i < arrayMember.length; i++) {
            array(arrayMember[i]);
        }
        _resetIndex = resetIndexSaved;
        return (/*M*/Member[][][]) arrayMember;
    }

    /**
     * Defines the specified array of UTF-8 strings, all strings having the
     * specified length (convenience method).
     *
     * @param  array the string array.
     * @param stringLength the length of the string elements.
     * @return the specified string array.
     */
    protected UTF8String[] array(UTF8String[] array, int stringLength) {
        for (int i = 0; i < array.length; i++) {
            array[i] = new UTF8String(stringLength);
        }
        return array;
    }

    /**
     * Updates this struct indexes after adding a member with the
     * specified constraints.
     *
     * @param  alignment the desired alignment in bytes or <code>0</code>
     *         if no alignment performed (e.g. bit fields).
     * @param  nbrOfBits  the size in bits.
     * @return the bit offset of the member in its struct.
     */
    private int updateIndexes(int alignment, int nbrOfBits) {

        // Resets index if union.
        if (_resetIndex) {
            _bitIndex = 0;
        }

        // Updates Struct's maximum desired alignment if not packed.
        if (!isPacked() && (_alignment < alignment)) {
            _alignment = alignment;
        }

        // Add padding if required (alignment constraint).
        int bitOffset = _bitIndex;
        if (alignment != 0) { // Not a bit field.

            // Calculates true alignment.
            int trueAlignment = isPacked() ? 1 : MathLib.min(alignment, ((Integer) MAXIMUM_ALIGNMENT.get()).intValue());

            // Calculates padding due to alignment constraints.
            int i = _bitIndex % (trueAlignment << 3);
            int paddingBits = (i == 0) ? 0 : // Aligned.
                    (trueAlignment << 3) - i; // Bits to next boundary.
            bitOffset += paddingBits;
        }

        // Updates struct bit index.
        _bitIndex = bitOffset + nbrOfBits;

        // Updates bits used (for size calculation in Union).
        if (_bitsUsed < _bitIndex) {
            _bitsUsed = _bitIndex;
        }

        return bitOffset;
    }

    /**
     * Reads the specified bits from this Struct as an integer value.
     *
     * @param  bitOffset the bit start position in the Struct.
     * @param  bitSize the number of bits.
     * @return the specified bits read as a signed long.
     * @throws IllegalArgumentException if
     *         <code>(bitOffset + bitSize - 1) / 8 >= this.size()</code>
     */
    public long readBits(int bitOffset, int bitSize) {
        if ((bitOffset + bitSize - 1) >> 3 >= this.size())
            throw new IllegalArgumentException("Attempt to read outside the Struct");
        int offset = bitOffset >> 3;
        int bitStart = bitOffset - (offset << 3);
        bitStart = (byteOrder() == ByteOrder.BIG_ENDIAN) ? bitStart : 64 - bitSize - bitStart;
        int index = getByteBufferPosition() + offset;
        long value = readByteBufferLong(index);
        value <<= bitStart; // Clears preceding bits
        value >>= (64 - bitSize); // Signed shift.
        return value;
    }

    private long readByteBufferLong(int index) {
        ByteBuffer byteBuffer = getByteBuffer();
        if (index + 8 < byteBuffer.capacity())
            return byteBuffer.getLong(index);
        // Else possible buffer overflow.
        if (byteBuffer.order() == ByteOrder.LITTLE_ENDIAN) {
            return (readByte(index, byteBuffer) & 0xff) + ((readByte(++index, byteBuffer) & 0xff) << 8) + ((readByte(++index, byteBuffer) & 0xff) << 16) + ((readByte(++index, byteBuffer) & 0xffL) << 24) + ((readByte(++index, byteBuffer) & 0xffL) << 32) + ((readByte(++index, byteBuffer) & 0xffL) << 40) + ((readByte(++index, byteBuffer) & 0xffL) << 48) + ((readByte(++index, byteBuffer) & 0xffL) << 56);
        } else {
            return (((long) readByte(index, byteBuffer)) << 56) + ((readByte(++index, byteBuffer) & 0xffL) << 48) + ((readByte(++index, byteBuffer) & 0xffL) << 40) + ((readByte(++index, byteBuffer) & 0xffL) << 32) + ((readByte(++index, byteBuffer) & 0xffL) << 24) + ((readByte(++index, byteBuffer) & 0xff) << 16) + ((readByte(++index, byteBuffer) & 0xff) << 8) + (readByte(++index, byteBuffer) & 0xffL);
        }
    }

    private static byte readByte(int index, ByteBuffer byteBuffer) {
        return (index < byteBuffer.capacity()) ? byteBuffer.get(index) : 0;
    }

    /**
     * Writes the specified bits into this Struct.
     *
     * @param  value the bits value as a signed long.
     * @param  bitOffset the bit start position in the Struct.
     * @param  bitSize the number of bits.
     * @throws IllegalArgumentException if
     *         <code>(bitOffset + bitSize - 1) / 8 >= this.size()</code>
     */
    public void writeBits(long value, int bitOffset, int bitSize) {
        if ((bitOffset + bitSize - 1) >> 3 >= this.size())
            throw new IllegalArgumentException("Attempt to write outside the Struct");
        int offset = bitOffset >> 3;
        int bitStart = (byteOrder() == ByteOrder.BIG_ENDIAN) ? bitOffset - (offset << 3)
                : 64 - bitSize - (bitOffset - (offset << 3));
        long mask = -1L;
        mask <<= bitStart; // Clears preceding bits
        mask >>>= (64 - bitSize); // Unsigned shift.
        mask <<= 64 - bitSize - bitStart;
        int index = getByteBufferPosition() + offset;
        long oldValue = readByteBufferLong(index);
        long resetValue = oldValue & (~mask);
        long newValue = resetValue | (value << (64 - bitSize - bitStart));
        writeByteBufferLong(index, newValue);
    }

    private void writeByteBufferLong(int index, long value) {
        ByteBuffer byteBuffer = getByteBuffer();
        if (index + 8 < byteBuffer.capacity()) {
            byteBuffer.putLong(index, value);
            return;
        }
        // Else possible buffer overflow.
        if (byteBuffer.order() == ByteOrder.LITTLE_ENDIAN) {
            writeByte(index, byteBuffer, (byte) value);
            writeByte(++index, byteBuffer, (byte) (value >> 8));
            writeByte(++index, byteBuffer, (byte) (value >> 16));
            writeByte(++index, byteBuffer, (byte) (value >> 24));
            writeByte(++index, byteBuffer, (byte) (value >> 32));
            writeByte(++index, byteBuffer, (byte) (value >> 40));
            writeByte(++index, byteBuffer, (byte) (value >> 48));
            writeByte(++index, byteBuffer, (byte) (value >> 56));
        } else {
            writeByte(index, byteBuffer, (byte) (value >> 56));
            writeByte(++index, byteBuffer, (byte) (value >> 48));
            writeByte(++index, byteBuffer, (byte) (value >> 40));
            writeByte(++index, byteBuffer, (byte) (value >> 32));
            writeByte(++index, byteBuffer, (byte) (value >> 24));
            writeByte(++index, byteBuffer, (byte) (value >> 16));
            writeByte(++index, byteBuffer, (byte) (value >> 8));
            writeByte(++index, byteBuffer, (byte) value);
        }
    }

    private static void writeByte(int index, ByteBuffer byteBuffer, byte value) {
        if (index < byteBuffer.capacity()) {
            byteBuffer.put(index, value);
        }
    }

    /////////////
    // MEMBERS //
    /////////////
    /**
     * This inner class represents the base class for all {@link Struct}
     * members. It allows applications to define additional member types.
     * For example:[code]
     *    public class MyStruct extends Struct {
     *        BitSet bits = new BitSet(256);
     *        ...
     *        public BitSet extends Member {
     *            public BitSet(int nbrBits) {
     *                super(1, (nbrBits+7)>>3);
     *            }
     *            public boolean get(int i) { ... }
     *            public void set(int i, boolean value) { ...}
     *        }
     *    }[/code]
     */
    protected class Member {

        /**
         * Holds the relative bit offset of this member within its struct.
         */
        private final int _bitOffset;

        /**
         * Holds the bit size of this member.
         */
        private final int _bitSize;

        /**
         * Base constructor for custom member types.
         *
         * @param  alignment the desired alignment in bytes or <code>0</code>
         *         if no alignment to be performed (e.g. bit fields).
         * @param  bitSize the size of this member in bytes.
         */
        protected Member(int alignment, int bitSize) {
            _bitSize = bitSize;
            _bitOffset = updateIndexes(alignment, bitSize);
        }

        /**
         * Returns the outer {@link Struct struct} container.
         *
         * @return the outer struct.
         */
        public final Struct struct() {
            return Struct.this;
        }

        /**
         * Returns the number of bits in this member
         *
         * @return the number of bits in the Member
         */
        public final int bitSize() {
            return _bitSize;
        }

        /**
         * Returns the bit offset of this member in its struct.
         *
         * @return the number of bits in the Member
         */
        public final int bitOffset() {
            return _bitOffset;
        }
    }

    ///////////////////////
    // PREDEFINED FIELDS //
    ///////////////////////
    /**
     * This class represents a UTF-8 character string, null terminated
     * (for C/C++ compatibility)
     */
    public class UTF8String extends Member {

        private final UTF8ByteBufferWriter _writer = new UTF8ByteBufferWriter();

        private final UTF8ByteBufferReader _reader = new UTF8ByteBufferReader();

        private final int _length;

        public UTF8String(int length) {
            super(1, length << 3);
            _length = length; // Takes into account 0 terminator.
        }

        public void set(String string) {
            final ByteBuffer buffer = getByteBuffer();
            synchronized (buffer) {
                try {
                    int index = getByteBufferPosition() + (bitOffset() >> 3);
                    buffer.position(index);
                    _writer.setOutput(buffer);
                    if (string.length() < _length) {
                        _writer.write(string);
                        _writer.write(0); // Marks end of string.
                    } else if (string.length() > _length) { // Truncates.
                        _writer.write(string.substring(0, _length));
                    } else { // Exact same length.
                        _writer.write(string);
                    }
                } catch (IOException e) { // Should never happen.
                    throw new Error(e.getMessage());
                } finally {
                    _writer.reset();
                }
            }
        }

        public String get() {
            final ByteBuffer buffer = getByteBuffer();
            synchronized (buffer) {
                TextBuilder tmp = TextBuilder.newInstance();
                try {
                    int index = getByteBufferPosition() + (bitOffset() >> 3);
                    buffer.position(index);
                    _reader.setInput(buffer);
                    for (int i = 0; i < _length; i++) {
                        char c = (char) _reader.read();
                        if (c == 0) { // Null terminator.
                            return tmp.toString();
                        } else {
                            tmp.append(c);
                        }
                    }
                    return tmp.toString();
                } catch (IOException e) { // Should never happen.
                    throw new Error(e.getMessage());
                } finally {
                    _reader.reset();
                    TextBuilder.recycle(tmp);
                }
            }
        }

        public String toString() {
            return this.get();
        }
    }

    /**
     * This class represents a 8 bits boolean with <code>true</code> represented
     * by <code>1</code> and <code>false</code> represented by <code>0</code>.
     */
    public class Bool extends Member {

        public Bool() {
            super(1, 8);
        }

        public Bool(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public boolean get() {
            if (bitSize() == 8) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return getByteBuffer().get(index) != 0;
            } else { // Else bitfields
                return readBits(bitOffset(), bitSize()) != 0;
            }
        }

        public void set(boolean value) {
            if (bitSize() == 8) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().put(index, (byte) (value ? -1 : 0));
            } else { // Else bitfields
                writeBits(value ? -1 : 0, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 8 bits signed integer.
     */
    public class Signed8 extends Member {

        public Signed8() {
            super(1, 8);
        }

        public Signed8(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public byte get() {
            if (bitSize() == 8) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return getByteBuffer().get(index);
            } else { // Else bitfields
                return (byte) readBits(bitOffset(), bitSize());
            }
        }

        public void set(byte value) {
            if (bitSize() == 8) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().put(index, value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 8 bits unsigned integer.
     */
    public class Unsigned8 extends Member {

        public Unsigned8() {
            super(1, 8);
        }

        public Unsigned8(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public short get() {
            if (bitSize() == 8) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return (short) (0xFF & getByteBuffer().get(index));
            } else { // Else bitfields
                long signedValue = readBits(bitOffset(), bitSize());
                return (short) (0xFF & signedValue);
            }
        }

        public void set(short value) {
            if (bitSize() == 8) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().put(index, (byte) value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 16 bits signed integer.
     */
    public class Signed16 extends Member {

        public Signed16() {
            super(2, 16);
        }

        public Signed16(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public short get() {
            if (bitSize() == 16) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return getByteBuffer().getShort(index);
            } else { // Else bitfields
                return (short) readBits(bitOffset(), bitSize());
            }
        }

        public void set(short value) {
            if (bitSize() == 16) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().putShort(index, value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 16 bits unsigned integer.
     */
    public class Unsigned16 extends Member {

        public Unsigned16() {
            super(2, 16);
        }

        public Unsigned16(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public int get() {
            if (bitSize() == 16) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return (int) (0xFFFF & getByteBuffer().getShort(index));
            } else { // Else bitfields
                long signedValue = readBits(bitOffset(), bitSize());
                return (int) (0xFFFF & signedValue);
            }
        }

        public void set(int value) {
            if (bitSize() == 16) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().putShort(index, (short) value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 32 bits signed integer.
     */
    public class Signed32 extends Member {

        public Signed32() {
            super(4, 32);
        }

        public Signed32(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public int get() {
            if (bitSize() == 32) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return getByteBuffer().getInt(index);
            } else { // Else bitfields
                return (int) readBits(bitOffset(), bitSize());
            }
        }

        public void set(int value) {
            if (bitSize() == 32) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().putInt(index, value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 32 bits unsigned integer.
     */
    public class Unsigned32 extends Member {

        public Unsigned32() {
            super(4, 32);
        }

        public Unsigned32(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public long get() {
            if (bitSize() == 32) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return 0xFFFFFFFFL & getByteBuffer().getInt(index);
            } else { // Else bitfields
                long signedValue = readBits(bitOffset(), bitSize());
                return 0xFFFFFFFFL & signedValue;
            }
        }

        public void set(long value) {
            if (bitSize() == 32) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().putInt(index, (int) value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 64 bits signed integer.
     */
    public class Signed64 extends Member {

        public Signed64() {
            super(8, 64);
        }

        public Signed64(int nbrOfBits) {
            super(0, nbrOfBits);
        }

        public long get() {
            if (bitSize() == 64) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                return getByteBuffer().getLong(index);
            } else { // Else bitfields
                return readBits(bitOffset(), bitSize());
            }
        }

        public void set(long value) {
            if (bitSize() == 64) {
                int index = (bitOffset() >> 3) + getByteBufferPosition();
                getByteBuffer().putLong(index, value);
            } else { // Else bitfields
                writeBits(value, bitOffset(), bitSize());
            }
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents an arbitrary size (unsigned) bit field with
     * no alignment constraint. Bit fields may cross words boundaries.
     * BitField values are unsigned but the maximum number of bits is 63
     * to hold in a long value.
     */
    public class BitField extends Member {

        public BitField(int nbrOfBits) {
            super(0, nbrOfBits);
            if (nbrOfBits >= 64) {
                throw new IllegalArgumentException(
                        "Unsigned bit fields cannot exceed 63 bits");
            }
        }

        public long longValue() {
            long signedValue = readBits(bitOffset(), bitSize());
            return (-1L >>> (64 - bitSize())) & signedValue;
        }

        public int intValue() {
            return (int) longValue();
        }

        public short shortValue() {
            return (short) longValue();
        }

        public byte byteValue() {
            return (byte) longValue();
        }

        public void set(long value) {
            writeBits(value, bitOffset(), bitSize());
        }

        public String toString() {
            return String.valueOf(longValue());
        }
    }

    /**
     * This class represents a 32 bits float (C/C++/Java <code>float</code>).
     */
    public class Float32 extends Member {

        public Float32() {
            super(4, 32);
        }

        public float get() {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            return getByteBuffer().getFloat(index);
        }

        public void set(float value) {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            getByteBuffer().putFloat(index, value);
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 64 bits float (C/C++/Java <code>double</code>).
     */
    public class Float64 extends Member {

        public Float64() {
            super(8, 8);
        }

        public double get() {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            return getByteBuffer().getDouble(index);
        }

        public void set(double value) {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            getByteBuffer().putDouble(index, value);
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * <p> This class represents a 32 bits reference (C/C++ pointer) to
     *     a {@link Struct} object (other types may require a {@link Struct}
     *     wrapper).</p>
     * <p> Note: For references which can be externally modified, an application
     *           may want to check the {@link #isUpToDate up-to-date} status of
     *           the reference. For out-of-date references, a {@link Struct}
     *           can be created at the address specified by {@link #value}
     *           (using JNI) and the reference {@link #set set} accordingly.</p>
     */
    public class Reference32/*<S extends Struct>*/ extends Member {

        private/*S*/ Struct _struct;

        public Reference32() {
            super(4, 32);
        }

        public void set(/*S*/Struct struct) {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            if (struct != null) {
                getByteBuffer().putInt(index, (int) struct.address());
            } else {
                getByteBuffer().putInt(index, 0);
            }
            _struct = struct;
        }

        public/*S*/ Struct get() {
            return _struct;
        }

        public int value() {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            return getByteBuffer().getInt(index);
        }

        public boolean isUpToDate() {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            if (_struct != null) {
                return getByteBuffer().getInt(index) == (int) _struct.address();
            } else {
                return getByteBuffer().getInt(index) == 0;
            }
        }
    }

    /**
     * <p> This class represents a 64 bits reference (C/C++ pointer) to
     *     a {@link Struct} object (other types may require a {@link Struct}
     *     wrapper).</p>
     * <p> Note: For references which can be externally modified, an application
     *           may want to check the {@link #isUpToDate up-to-date} status of
     *           the reference. For out-of-date references, a new {@link Struct}
     *           can be created at the address specified by {@link #value}
     *           (using JNI) and then {@link #set set} to the reference.</p>
     */
    public class Reference64/*<S extends Struct>*/ extends Member {

        private/*S*/ Struct _struct;

        public Reference64() {
            super(8, 64);
        }

        public void set(/*S*/Struct struct) {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            if (struct != null) {
                getByteBuffer().putLong(index, struct.address());
            } else if (struct == null) {
                getByteBuffer().putLong(index, 0L);
            }
            _struct = struct;
        }

        public/*S*/ Struct get() {
            return _struct;
        }

        public long value() {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            return getByteBuffer().getLong(index);
        }

        public boolean isUpToDate() {
            int index = (bitOffset() >> 3) + getByteBufferPosition();
            if (_struct != null) {
                return getByteBuffer().getLong(index) == _struct.address();
            } else {
                return getByteBuffer().getLong(index) == 0L;
            }
        }
    }

    /**
     * This class represents a 8 bits {@link Enum}.
     */
    public class Enum8 extends Member {

        private final List _enumValues;

        public Enum8(List enumValues) {
            super(1, 8);
            _enumValues = enumValues;
        }

        public Enum8(List enumValues, int nbrOfBits) {
            super(0, nbrOfBits);
            _enumValues = enumValues;
        }

        public Enum get() {
            long signedValue = readBits(bitOffset(), bitSize());
            int index = (int) ((-1L >>> (64 - bitSize())) & signedValue);
            return (Enum) _enumValues.get(index);
        }

        public void set(Enum e) {
            int index = e.ordinal();
            if (_enumValues.get(index) != e)
                throw new IllegalArgumentException(
                        "enum: " + e + ", ordinal value does not reflect enum values position");
            writeBits(index, bitOffset(), bitSize());
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 16 bits {@link Enum}.
     */
    public class Enum16 extends Member {

        private final List _enumValues;

        public Enum16(List enumValues) {
            super(1, 16);
            _enumValues = enumValues;
        }

        public Enum16(List enumValues, int nbrOfBits) {
            super(0, nbrOfBits);
            _enumValues = enumValues;
        }

        public Enum get() {
            long signedValue = readBits(bitOffset(), bitSize());
            int index = (int) ((-1L >>> (64 - bitSize())) & signedValue);
            return (Enum) _enumValues.get(index);
        }

        public void set(Enum e) {
            int index = e.ordinal();
            if (_enumValues.get(index) != e)
                throw new IllegalArgumentException(
                        "enum: " + e + ", ordinal value does not reflect enum values position");
            writeBits(index, bitOffset(), bitSize());
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 32 bits {@link Enum}.
     */
    public class Enum32 extends Member {

        private final List _enumValues;

        public Enum32(List enumValues) {
            super(1, 32);
            _enumValues = enumValues;
        }

        public Enum32(List enumValues, int nbrOfBits) {
            super(0, nbrOfBits);
            _enumValues = enumValues;
        }

        public Enum get() {
            long signedValue = readBits(bitOffset(), bitSize());
            int index = (int) ((-1L >>> (64 - bitSize())) & signedValue);
            return (Enum) _enumValues.get(index);
        }

        public void set(Enum e) {
            int index = e.ordinal();
            if (_enumValues.get(index) != e)
                throw new IllegalArgumentException(
                        "enum: " + e + ", ordinal value does not reflect enum values position");
            writeBits(index, bitOffset(), bitSize());
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }

    /**
     * This class represents a 64 bits {@link Enum}.
     */
    public class Enum64 extends Member {

        private final List _enumValues;

        public Enum64(List enumValues) {
            super(1, 64);
            _enumValues = enumValues;
        }

        public Enum64(List enumValues, int nbrOfBits) {
            super(0, nbrOfBits);
            _enumValues = enumValues;
        }

        public Enum get() {
            long signedValue = readBits(bitOffset(), bitSize());
            int index = (int) ((-1L >>> (64 - bitSize())) & signedValue);
            return (Enum) _enumValues.get(index);
        }

        public void set(Enum e) {
            int index = e.ordinal();
            if (_enumValues.get(index) != e)
                throw new IllegalArgumentException(
                        "enum: " + e + ", ordinal value does not reflect enum values position");
            writeBits(index, bitOffset(), bitSize());
        }

        public String toString() {
            return String.valueOf(this.get());
        }
    }
}
