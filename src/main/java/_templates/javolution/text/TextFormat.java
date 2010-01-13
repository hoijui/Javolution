/*
 * Javolution - Java(TM) Solution for Real-Time and Embedded Systems
 * Copyright (C) 2006 - Javolution (http://javolution.org/)
 * All rights reserved.
 * 
 * Permission to use, copy, modify, and distribute this software is
 * freely granted, provided that this notice is preserved.
 */
package _templates.javolution.text;

import java.io.IOException;

import _templates.java.lang.CharSequence;
import _templates.javolution.Javolution;
import _templates.javolution.context.LocalContext;
import _templates.javolution.lang.ClassInitializer;
import _templates.javolution.lang.Reflection;
import _templates.javolution.text.Appendable;
import _templates.javolution.util.LocalMap;
import _templates.java.lang.ThreadLocal;

/**
 * <p> This class represents the base format for text parsing and formatting; 
 *     it supports the {@link CharSequence} and {@link Appendable} interfaces 
 *     for greater flexibility.</p>
 * 
 * <p> Typically, classes for which textual parsing/formatting is supported
 *     have a <code>protected static final</code> TextFormat instance
 *     holding the default format for the class. For input/output
 *     (e.g. valueOf(CharSequence), toString()), the {@link LocalContext
 *     context-local} format (or current format) should be used.
 *     [code]
 *     public class Complex implements ValueType {
 *         // Defines the default format for Complex (cartesian).
 *         protected static final TextFormat<Complex> TEXT_FORMAT = new TextFormat<Complex>(Complex.class) {
 *             ...
 *         }
 *         public Complex valueOf(CharSequence csq) {
 *             // Uses the local format for Complex numbers.
 *             return TextFormat.getInstance(Complex.class).parse(csq);
 *         }
 *         public Text toText() {
 *             // Uses the local format for Complex numbers.
 *             return TextFormat.getInstance(Complex.class).format(this);
 *         }
 *     }
 *     [/code]
 * <p> It is possible to retrieve either the {@link #getDefault default} format 
 *     for any class or the {@link #getInstance current} format which
 *     can be locally overriden on a thread basis.
 *     [code]
 *     Complex c = Complex.valueOf(3, 4);
 *     System.out.println(c); // Display using the default cartesian format (e.g. "2.1 - 3.2i")
 *     TextFormat<Complex> polarFormat = new TextFormat<Complex>(null) { ... }; // Creates an unbounded format.
 *     LocalContext.enter();
 *     try {
 *         TextFormat.setInstance(polarFormat, Complex.class); // Context-local setting.
 *         System.out.println(c); // Display using the local polar format.
 *     } finally {
 *         LocalContext.exit(); // Reverts to the default cartesian format for complex numbers.
 *     }
 *     [/code]</p>
 *
 * <p> For parsing/formatting of primitive types, the {@link TypeFormat}
 *     utility class is recommended.</p>
 *     
 * @author <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle </a>
 * @version 5.4, November 2, 2009
 */
public abstract class TextFormat/*<T>*/ {

    /**
     * Holds the class to format mapping (context local).
     */
    private static final LocalMap CLASS_TO_FORMAT = new LocalMap();

    /**
     * Holds the class associated to this format (static instances)
     * or <code>null</code> if the format is unbound.
     */
    private final Class/*<T>*/ _class;

    /**
     * Creates a new text format and if a class is specified, makes it the
     * default format for all instances of the class. The actual format (local)
     * for a given class can be retrieved through the
     * {@link #getInstance(java.lang.Class)} static method.
     *
     * @param cls the class for which this format is the default format or
     *        <code>null</code> to return an unbounded format.
     * @throws IllegalArgumentException if the specified class has already a
     *         default text format associated with (which can only be overriden
     *         by local formats, see {@link #setInstance}
     */
    protected TextFormat(Class/*<T>*/ cls) {
        _class = cls;
        if (cls == null)
            return;
        if (CLASS_TO_FORMAT.containsKey(cls))
            throw new IllegalArgumentException(
                    "Class " + cls + " has already a default text format, " +
                    "only local override using the TextFormat.setInstance(...)" +
                    " method is allowed.");
        CLASS_TO_FORMAT.putDefault(cls, this);
    }

    /**
     * <p> Returns the default text format for instances of specified type.
     *     If there is no text format for the specified type, a text format
     *     for the implementing interfaces is searched. If still none is found
     *     a recurcive search is performed on the parent class.</p>
     *
     * <p> Default format instances are typically defined as <code>protected
     *     static final</code> instances in their {@link #getBoundClass() bound}
     *     class.</code></p>
     *
     * <p> The following predefined types have a default format:<code><ul>
     *       <li>java.lang.Object (formatting only)</li>
     *       <li>java.lang.String</li>
     *       <li>java.lang.Boolean</li>
     *       <li>java.lang.Character</li>
     *       <li>java.lang.Byte</li>
     *       <li>java.lang.Short</li>
     *       <li>java.lang.Integer</li>
     *       <li>java.lang.Long</li>
     *       <li>java.lang.Float</li>
     *       <li>java.lang.Double</li>
     *       <li>java.lang.Class</li>
     *     </ul></code></p>
     *
     * @param  forClass the class for which the default format is returned.
     * @return the default format for instances of the specified class or
     *         <code>null</code> is none found for the class itself, its
     *          parent classes or implementing interfaces.
     */
    public static /*<T>*/ TextFormat/*<T>*/ getDefault(Class/*<? extends T>*/ forClass) {
        TextFormat format = (TextFormat) CLASS_TO_FORMAT.getDefault(forClass);
        if (format != null)
            return format;
        ClassInitializer.initialize(forClass); // Ensures class static initializer are run.
        return searchDefaultFormat(forClass);
    }

    private static TextFormat searchDefaultFormat(Class forClass) {
        // Searches if format associated to the class itself.
        TextFormat format = (TextFormat) CLASS_TO_FORMAT.getDefault(forClass);
        if (format != null)
            return format;

        // Searches the format associated to the implementing interface.
        Class[] interfaces = Reflection.getInstance().getInterfaces(forClass);
        for (int i = 0; i < interfaces.length; i++) {
            format = (TextFormat) CLASS_TO_FORMAT.getDefault(interfaces[i]);
            if (format != null)
                return format;
        }

        // Recursion with the parent class.
        Class parentClass = Reflection.getInstance().getSuperclass(forClass);
        return parentClass != null ? searchDefaultFormat(parentClass) : null;
    }

    /**
     * <p> Returns the current text format for instances of specified type.
     *     If there is no text format for the specified type, a text format
     *     for the implementing interfaces is searched. If still none is found
     *     a recurcive search is performed on the parent class.</p>
     *
     * <p> If the text format has not been {@link #setInstance
     *    overriden} then the {@link #getDefault default} is returned.</p>
     *
     * @param  forClass the class for which the current format is returned.
     * @return the current format for instances of the specified class or
     *         <code>null</code> if none.
     * @see LocalMap
     */
    public static/*<T>*/ TextFormat/*<T>*/ getInstance(Class/*<? extends T>*/ forClass) {
        TextFormat format = (TextFormat) CLASS_TO_FORMAT.get(forClass);
        if (format != null)
            return format;
        ClassInitializer.initialize(forClass); // Ensures class static initializer are run.
        return searchLocalFormat(forClass);
    }

    private static TextFormat searchLocalFormat(Class forClass) {
        // Searches if format associated to the class itself.
        TextFormat format = (TextFormat) CLASS_TO_FORMAT.get(forClass);
        if (format != null)
            return format;

        // Searches the format associated to the implementing interface.
        Class[] interfaces = Reflection.getInstance().getInterfaces(forClass);
        for (int i = 0; i < interfaces.length; i++) {
            format = (TextFormat) CLASS_TO_FORMAT.get(interfaces[i]);
            if (format != null)
                return format;
        }

        // Recursion with the parent class.
        Class parentClass = Reflection.getInstance().getSuperclass(forClass);
        return parentClass != null ? searchLocalFormat(parentClass) : null;
    }

    /**
     * Overrides the current format for the specified class
     * ({@link javolution.context.LocalContext context-local}).
     * 
     * @param format the format for instances of the specified class.
     * @param forClass the class for which the text format is overriden.
     * @see #getInstance
     * @see LocalMap
     */
    public static/*<T>*/ void setInstance(TextFormat/*<T>*/ format, Class/*<T>*/ forClass) {
        CLASS_TO_FORMAT.put(forClass, format); // Local setting.
    }

    /**
     * Returns the class/interface statically bound to this format or
     * <code>null</code> if this text format is not the default format
     * for the specified class.
     *
     * @return the class/interface bound to this format or <code>null</code>
     */
    public final Class/*<T>*/ getBoundClass() {
        return _class;
    }

    /**
     * Formats the specified object into an <code>Appendable</code> 
     * 
     * @param obj the object to format.
     * @param dest the appendable destination.
     * @return the specified <code>Appendable</code>.
     * @throws IOException if an I/O exception occurs.
     */
    public abstract Appendable format(Object/*{T}*/ obj, Appendable dest)
            throws IOException;

    /**
     * Parses a portion of the specified <code>CharSequence</code> from the
     * specified position to produce an object. If parsing succeeds, then the
     * index of the <code>cursor</code> argument is updated to the index after
     * the last character used. 
     * 
     * @param csq the <code>CharSequence</code> to parse.
     * @param cursor the cursor holding the current parsing index.
     * @return the object parsed from the specified character sub-sequence.
     * @throws IllegalArgumentException if any problem occurs while parsing the
     *         specified character sequence (e.g. illegal syntax).
     */
    public abstract Object/*{T}*/ parse(CharSequence csq, Cursor cursor) throws IllegalArgumentException;

    /**
     * Formats the specified object into a {@link TextBuilder} (convenience 
     * method which does not raise IOException). 
     * 
     * @param obj the object to format.
     * @param dest the text builder destination.
     * @return the specified text builder.
     */
    public final Appendable format(Object/*{T}*/ obj, TextBuilder dest) {
        try {
            return format(obj, (Appendable) dest);
        } catch (IOException e) {
            throw new Error(); // Cannot happen.
        }
    }

    /**
     * Formats the specified object to a {@link Text} instance
     * (convenience method equivalent to 
     * <code>format(obj, TextBuilder.newInstance()).toText()</code>).
     * 
     * @param obj the object being formated.
     * @return the text representing the specified object.
     */
    public final Text format(Object/*{T}*/ obj) {
        TextBuilder tb = TextBuilder.newInstance();
        try {
            format(obj, tb);
            return tb.toText();
        } finally {
            TextBuilder.recycle(tb);
        }
    }

    /**
     * Parses a whole character sequence from the beginning to produce an object
     * (convenience method). 
     * 
     * @param csq the whole character sequence to parse.
     * @return <code>parse(csq, new Cursor())</code>
     * @throws IllegalArgumentException if the specified character sequence
     *        cannot be fully parsed (e.g. extraneous characters).
     */
    public final Object/*{T}*/ parse(CharSequence csq) throws IllegalArgumentException {
        Cursor cursor = Cursor.newInstance();
        try {
            Object/*{T}*/ obj = parse(csq, cursor);
            if (cursor.getIndex() < csq.length())
                throw new IllegalArgumentException(
                        "Extraneous characters in \"" + csq + "\"");
            return obj;
        } finally {
            Cursor.recycle(cursor);
        }
    }

    /**
     * Returns textual information about this format.
     *
     * @return this format textual information.
     */
    public String toString() {
        Class boundClass = getBoundClass();
        return (boundClass != null) ? "Default TextFormat for " + boundClass.getName() : "Dynamic TextFormat (" + this.hashCode() + ")";
    }
    // Predefined formats.
    static final TextFormat OBJECT_FORMAT =
            new TextFormat(Object.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    if (RECURSION_CHECK.get().equals(Boolean.TRUE))
                        throw new _templates.java.lang.UnsupportedOperationException(
                                "Infinite recursion detected. TextFormat for Object.class " +
                                "uses toString() which in turn uses TextFormat." +
                                "A specialized TextFormat for the class " + obj.getClass() + " should be defined");
                    RECURSION_CHECK.set(Boolean.TRUE);
                    try {
                        String str = obj.toString();
                        return dest.append(Javolution.j2meToCharSeq(str));
                    } finally {
                        RECURSION_CHECK.set(Boolean.FALSE);
                    }
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    throw new _templates.java.lang.UnsupportedOperationException(
                            "TextFormat for object to be parsed is missing");
                }
            };

    private static final ThreadLocal RECURSION_CHECK = new ThreadLocal();

    static final TextFormat STRING_FORMAT =
            new TextFormat(String.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return dest.append(Javolution.j2meToCharSeq(obj));
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return csq.subSequence(cursor.getIndex(), csq.length()).toString();
                }
            };

    static final TextFormat BOOLEAN_FORMAT =
            new TextFormat(Boolean.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return TypeFormat.format(((Boolean) obj).booleanValue(), dest);
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return TypeFormat.parseBoolean(csq, cursor) ? Boolean.TRUE : Boolean.FALSE;
                }
            };

    static final TextFormat CHARACTER_FORMAT =
            new TextFormat(Character.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return dest.append(((Character) obj).charValue());
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return new Character(cursor.nextChar(csq));
                }
            };

    static final TextFormat BYTE_FORMAT =
            new TextFormat(Byte.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return TypeFormat.format(((Byte) obj).byteValue(), dest);
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return new Byte(TypeFormat.parseByte(csq, 10, cursor));
                }
            };

    static final TextFormat SHORT_FORMAT =
            new TextFormat(Short.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return TypeFormat.format(((Short) obj).shortValue(), dest);
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return new Short(TypeFormat.parseShort(csq, 10, cursor));
                }
            };

    static final TextFormat INTEGER_FORMAT =
            new TextFormat(Integer.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return TypeFormat.format(((Integer) obj).intValue(), dest);
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return new Integer(TypeFormat.parseInt(csq, 10, cursor));
                }
            };

    static final TextFormat LONG_FORMAT =
            new TextFormat(Long.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return TypeFormat.format(((Long) obj).longValue(), dest);
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return new Long(TypeFormat.parseLong(csq, 10, cursor));
                }
            };

    static final TextFormat FLOAT_FORMAT = new TextFormat(Float.class) {

        public Appendable format(Object obj, Appendable dest)
                throws IOException {
            return TypeFormat.format(((Float) obj).floatValue(), dest);
        }

        public Object parse(CharSequence csq, Cursor cursor) {
            return new Float(TypeFormat.parseFloat(csq, cursor));
        }
    };

    static final TextFormat DOUBLE_FORMAT =
            new TextFormat(Double.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return TypeFormat.format(((Double) obj).doubleValue(), dest);
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    return new Double(TypeFormat.parseDouble(csq, cursor));
                }
            };

    static final TextFormat CLASS_FORMAT =
            new TextFormat(Class.class) {

                public Appendable format(Object obj, Appendable dest)
                        throws IOException {
                    return dest.append(Javolution.j2meToCharSeq(((Class) obj).getName()));
                }

                public Object parse(CharSequence csq, Cursor cursor) {
                    CharSequence className = cursor.nextToken(csq, CharSet.WHITESPACES);
                    if (className == null)
                        throw new IllegalArgumentException("No class name found");
                    Class cls = Reflection.getInstance().getClass(className);
                    if (cls != null)
                        return cls;
                    throw new IllegalArgumentException("Class \"" + className + "\" not found (see javolution.lang.Reflection)");
                }
            };

}
