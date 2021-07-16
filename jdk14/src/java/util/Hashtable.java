
package java.util;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;
import sun.misc.SharedSecrets;

public class Hashtable<K,V> extends Dictionary<K,V>
    implements Map<K,V>, Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 1421746759512286392L;

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private transient Entry<?,?>[] table;
    private transient int count;
    private int threshold;
    private float loadFactor;
    private transient int modCount = 0;
    private transient volatile Set<K> keySet;
    private transient volatile Set<Map.Entry<K,V>> entrySet;
    private transient volatile Collection<V> values;
    private static final int KEYS = 0;
    private static final int VALUES = 1;
    private static final int ENTRIES = 2;

    public Hashtable() {
        this(11, 0.75f);
    }
    public Hashtable(Map<? extends K, ? extends V> t) {
        this(Math.max(2*t.size(), 11), 0.75f);
        putAll(t);
    }
    public Hashtable(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: "+ initialCapacity);
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load: "+loadFactor);

        if (initialCapacity==0)
            initialCapacity = 1;
        this.loadFactor = loadFactor;
        table = new Entry<?,?>[initialCapacity];
        threshold = (int)Math.min(initialCapacity * loadFactor, MAX_ARRAY_SIZE + 1);
    }
    public Hashtable(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    public synchronized int size() {
        return count;
    }
    public synchronized boolean isEmpty() {
        return count == 0;
    }
    public synchronized Enumeration<K> keys() {
        return this.<K>getEnumeration(KEYS);
    }
    public synchronized Enumeration<V> elements() {
        return this.<V>getEnumeration(VALUES);
    }
    public synchronized boolean contains(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }

        Entry<?,?> tab[] = table;
        for (int i = tab.length ; i-- > 0 ;) {
            for (Entry<?,?> e = tab[i] ; e != null ; e = e.next) {
                if (e.value.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean containsValue(Object value) {
        return contains(value);
    }
    public synchronized boolean containsKey(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                return true;
            }
        }
        return false;
    }
    public synchronized V get(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                return (V)e.value;
            }
        }
        return null;
    }
    public synchronized V put(K key, V value) {
        if (value == null) {
            throw new NullPointerException();
        }
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        Entry<K,V> entry = (Entry<K,V>)tab[index];
        for(; entry != null ; entry = entry.next) {
            if ((entry.hash == hash) && entry.key.equals(key)) {
                V old = entry.value;
                entry.value = value;
                return old;
            }
        }
        addEntry(hash, key, value, index);
        return null;
    }
    public synchronized V remove(Object key) {
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for(Entry<K,V> prev = null ; e != null ; prev = e, e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                modCount++;
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }
                count--;
                V oldValue = e.value;
                e.value = null;
                return oldValue;
            }
        }
        return null;
    }
    public synchronized void putAll(Map<? extends K, ? extends V> t) {
        for (Map.Entry<? extends K, ? extends V> e : t.entrySet())
            put(e.getKey(), e.getValue());
    }
    public synchronized void clear() {
        Entry<?,?> tab[] = table;
        modCount++;
        for (int index = tab.length; --index >= 0; )
            tab[index] = null;
        count = 0;
    }
    public synchronized Object clone() {
        try {
            Hashtable<?,?> t = (Hashtable<?,?>)super.clone();
            t.table = new Entry<?,?>[table.length];
            for (int i = table.length ; i-- > 0 ; ) {
                t.table[i] = (table[i] != null)
                    ? (Entry<?,?>) table[i].clone() : null;
            }
            t.keySet = null;
            t.entrySet = null;
            t.values = null;
            t.modCount = 0;
            return t;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }
    public synchronized String toString() {
        int max = size() - 1;
        if (max == -1)
            return "{}";

        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<K,V>> it = entrySet().iterator();

        sb.append('{');
        for (int i = 0; ; i++) {
            Map.Entry<K,V> e = it.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key   == this ? "(this Map)" : key.toString());
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value.toString());

            if (i == max)
                return sb.append('}').toString();
            sb.append(", ");
        }
    }
    public synchronized boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Map))
            return false;
        Map<?,?> t = (Map<?,?>) o;
        if (t.size() != size())
            return false;

        try {
            Iterator<Map.Entry<K,V>> i = entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<K,V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(t.get(key)==null && t.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(t.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused)   {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }
    public synchronized int hashCode() {
        int h = 0;
        if (count == 0 || loadFactor < 0)
            return h;  // Returns zero

        loadFactor = -loadFactor;  // Mark hashCode computation in progress
        Entry<?,?>[] tab = table;
        for (Entry<?,?> entry : tab) {
            while (entry != null) {
                h += entry.hashCode();
                entry = entry.next;
            }
        }

        loadFactor = -loadFactor;  // Mark hashCode computation complete

        return h;
    }
    public synchronized V getOrDefault(Object key, V defaultValue) {
        V result = get(key);
        return (null == result) ? defaultValue : result;
    }
    public synchronized void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);     // explicit check required in case
                                            // table is empty.
        final int expectedModCount = modCount;

        Entry<?, ?>[] tab = table;
        for (Entry<?, ?> entry : tab) {
            while (entry != null) {
                action.accept((K)entry.key, (V)entry.value);
                entry = entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }
    public synchronized void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);     // explicit check required in case
                                              // table is empty.
        final int expectedModCount = modCount;

        Entry<K, V>[] tab = (Entry<K, V>[])table;
        for (Entry<K, V> entry : tab) {
            while (entry != null) {
                entry.value = Objects.requireNonNull(
                    function.apply(entry.key, entry.value));
                entry = entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }
    public synchronized V putIfAbsent(K key, V value) {
        Objects.requireNonNull(value);

        // Makes sure the key is not already in the hashtable.
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> entry = (Entry<K,V>)tab[index];
        for (; entry != null; entry = entry.next) {
            if ((entry.hash == hash) && entry.key.equals(key)) {
                V old = entry.value;
                if (old == null) {
                    entry.value = value;
                }
                return old;
            }
        }

        addEntry(hash, key, value, index);
        return null;
    }
    public synchronized boolean remove(Object key, Object value) {
        Objects.requireNonNull(value);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if ((e.hash == hash) && e.key.equals(key) && e.value.equals(value)) {
                modCount++;
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }
                count--;
                e.value = null;
                return true;
            }
        }
        return false;
    }
    public synchronized boolean replace(K key, V oldValue, V newValue) {
        Objects.requireNonNull(oldValue);
        Objects.requireNonNull(newValue);
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                if (e.value.equals(oldValue)) {
                    e.value = newValue;
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }
    public synchronized V replace(K key, V value) {
        Objects.requireNonNull(value);
        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                V oldValue = e.value;
                e.value = value;
                return oldValue;
            }
        }
        return null;
    }
    public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (; e != null; e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                // Hashtable not accept null value
                return e.value;
            }
        }

        V newValue = mappingFunction.apply(key);
        if (newValue != null) {
            addEntry(hash, key, newValue, index);
        }

        return newValue;
    }
    public synchronized V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                V newValue = remappingFunction.apply(key, e.value);
                if (newValue == null) {
                    modCount++;
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        tab[index] = e.next;
                    }
                    count--;
                } else {
                    e.value = newValue;
                }
                return newValue;
            }
        }
        return null;
    }
    public synchronized V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && Objects.equals(e.key, key)) {
                V newValue = remappingFunction.apply(key, e.value);
                if (newValue == null) {
                    modCount++;
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        tab[index] = e.next;
                    }
                    count--;
                } else {
                    e.value = newValue;
                }
                return newValue;
            }
        }

        V newValue = remappingFunction.apply(key, null);
        if (newValue != null) {
            addEntry(hash, key, newValue, index);
        }

        return newValue;
    }
    public synchronized V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);

        Entry<?,?> tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>)tab[index];
        for (Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
            if (e.hash == hash && e.key.equals(key)) {
                V newValue = remappingFunction.apply(e.value, value);
                if (newValue == null) {
                    modCount++;
                    if (prev != null) {
                        prev.next = e.next;
                    } else {
                        tab[index] = e.next;
                    }
                    count--;
                } else {
                    e.value = newValue;
                }
                return newValue;
            }
        }

        if (value != null) {
            addEntry(hash, key, value, index);
        }

        return value;
    }

    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        Entry<Object, Object> entryStack = null;

        synchronized (this) {
            s.defaultWriteObject();

            s.writeInt(table.length);
            s.writeInt(count);

            for (int index = 0; index < table.length; index++) {
                Entry<?,?> entry = table[index];

                while (entry != null) {
                    entryStack =
                        new Entry<>(0, entry.key, entry.value, entryStack);
                    entry = entry.next;
                }
            }
        }

        while (entryStack != null) {
            s.writeObject(entryStack.key);
            s.writeObject(entryStack.value);
            entryStack = entryStack.next;
        }
    }


    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new StreamCorruptedException("Illegal Load: " + loadFactor);

        int origlength = s.readInt();
        int elements = s.readInt();

        if (elements < 0)
            throw new StreamCorruptedException("Illegal # of Elements: " + elements);


        origlength = Math.max(origlength, (int)(elements / loadFactor) + 1);

        int length = (int)((elements + elements / 20) / loadFactor) + 3;
        if (length > elements && (length & 1) == 0)
            length--;
        length = Math.min(length, origlength);

        if (length < 0) { // overflow
            length = origlength;
        }

        SharedSecrets.getJavaOISAccess().checkArray(s, Map.Entry[].class, length);
        table = new Entry<?,?>[length];
        threshold = (int)Math.min(length * loadFactor, MAX_ARRAY_SIZE + 1);
        count = 0;

        for (; elements > 0; elements--) {
            @SuppressWarnings("unchecked")
                K key = (K)s.readObject();
            @SuppressWarnings("unchecked")
                V value = (V)s.readObject();
            reconstitutionPut(table, key, value);
        }
    }


    private void reconstitutionPut(Entry<?,?>[] tab, K key, V value) throws StreamCorruptedException {
        if (value == null) {
            throw new java.io.StreamCorruptedException();
        }
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry<?,?> e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                throw new java.io.StreamCorruptedException();
            }
        }
        @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)tab[index];
        tab[index] = new Entry<>(hash, key, value, e);
        count++;
    }

    private class ValueCollection extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return getIterator(VALUES);
        }
        public int size() {
            return count;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            Hashtable.this.clear();
        }
    }
    private static class Entry<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        Entry<K,V> next;

        protected Entry(int hash, K key, V value, Entry<K,V> next) {
            this.hash = hash;
            this.key =  key;
            this.value = value;
            this.next = next;
        }

        @SuppressWarnings("unchecked")
        protected Object clone() {
            return new Entry<>(hash, key, value,
                                  (next==null ? null : (Entry<K,V>) next.clone()));
        }


        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
            if (value == null)
                throw new NullPointerException();

            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;

            return (key==null ? e.getKey()==null : key.equals(e.getKey())) &&
               (value==null ? e.getValue()==null : value.equals(e.getValue()));
        }

        public int hashCode() {
            return hash ^ Objects.hashCode(value);
        }

        public String toString() {
            return key.toString()+"="+value.toString();
        }
    }

    @SuppressWarnings("unchecked")
    protected void rehash() {
        int oldCapacity = table.length;
        Entry<?,?>[] oldMap = table;

        // overflow-conscious code
        int newCapacity = (oldCapacity << 1) + 1;
        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            if (oldCapacity == MAX_ARRAY_SIZE)
                // Keep running with MAX_ARRAY_SIZE buckets
                return;
            newCapacity = MAX_ARRAY_SIZE;
        }
        Entry<?,?>[] newMap = new Entry<?,?>[newCapacity];

        modCount++;
        threshold = (int)Math.min(newCapacity * loadFactor, MAX_ARRAY_SIZE + 1);
        table = newMap;

        for (int i = oldCapacity ; i-- > 0 ;) {
            for (Entry<K,V> old = (Entry<K,V>)oldMap[i] ; old != null ; ) {
                Entry<K,V> e = old;
                old = old.next;

                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                e.next = (Entry<K,V>)newMap[index];
                newMap[index] = e;
            }
        }
    }
    private void addEntry(int hash, K key, V value, int index) {
        modCount++;

        Entry<?,?> tab[] = table;
        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();

            tab = table;
            hash = key.hashCode();
            index = (hash & 0x7FFFFFFF) % tab.length;
        }

        @SuppressWarnings("unchecked")
        Entry<K,V> e = (Entry<K,V>) tab[index];
        tab[index] = new Entry<>(hash, key, value, e);
        count++;
    }


    private <T> Enumeration<T> getEnumeration(int type) {
        if (count == 0) {
            return Collections.emptyEnumeration();
        } else {
            return new Enumerator<>(type, false);
        }
    }

    private <T> Iterator<T> getIterator(int type) {
        if (count == 0) {
            return Collections.emptyIterator();
        } else {
            return new Enumerator<>(type, true);
        }
    }

    public Set<K> keySet() {
        if (keySet == null)
            keySet = Collections.synchronizedSet(new KeySet(), this);
        return keySet;
    }

    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return getIterator(KEYS);
        }
        public int size() {
            return count;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return Hashtable.this.remove(o) != null;
        }
        public void clear() {
            Hashtable.this.clear();
        }
    }


    public Set<Map.Entry<K,V>> entrySet() {
        if (entrySet==null)
            entrySet = Collections.synchronizedSet(new EntrySet(), this);
        return entrySet;
    }

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return getIterator(ENTRIES);
        }

        public boolean add(Map.Entry<K,V> o) {
            return super.add(o);
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
            Object key = entry.getKey();
            Entry<?,?>[] tab = table;
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % tab.length;

            for (Entry<?,?> e = tab[index]; e != null; e = e.next)
                if (e.hash==hash && e.equals(entry))
                    return true;
            return false;
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> entry = (Map.Entry<?,?>) o;
            Object key = entry.getKey();
            Entry<?,?>[] tab = table;
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % tab.length;

            @SuppressWarnings("unchecked")
            Entry<K,V> e = (Entry<K,V>)tab[index];
            for(Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
                if (e.hash==hash && e.equals(entry)) {
                    modCount++;
                    if (prev != null)
                        prev.next = e.next;
                    else
                        tab[index] = e.next;

                    count--;
                    e.value = null;
                    return true;
                }
            }
            return false;
        }

        public int size() {
            return count;
        }

        public void clear() {
            Hashtable.this.clear();
        }
    }

    public Collection<V> values() {
        if (values==null)
            values = Collections.synchronizedCollection(new ValueCollection(),
                    this);
        return values;
    }

    private class Enumerator<T> implements Enumeration<T>, Iterator<T> {
        Entry<?,?>[] table = Hashtable.this.table;
        int index = table.length;
        Entry<?,?> entry;
        Entry<?,?> lastReturned;
        int type;
        boolean iterator;
        protected int expectedModCount = modCount;

        Enumerator(int type, boolean iterator) {
            this.type = type;
            this.iterator = iterator;
        }

        public boolean hasMoreElements() {
            Entry<?,?> e = entry;
            int i = index;
            Entry<?,?>[] t = table;
            /* Use locals for faster loop iteration */
            while (e == null && i > 0) {
                e = t[--i];
            }
            entry = e;
            index = i;
            return e != null;
        }

        @SuppressWarnings("unchecked")
        public T nextElement() {
            Entry<?,?> et = entry;
            int i = index;
            Entry<?,?>[] t = table;
            /* Use locals for faster loop iteration */
            while (et == null && i > 0) {
                et = t[--i];
            }
            entry = et;
            index = i;
            if (et != null) {
                Entry<?,?> e = lastReturned = entry;
                entry = e.next;
                return type == KEYS ? (T)e.key : (type == VALUES ? (T)e.value : (T)e);
            }
            throw new NoSuchElementException("Hashtable Enumerator");
        }

        // Iterator methods
        public boolean hasNext() {
            return hasMoreElements();
        }

        public T next() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return nextElement();
        }

        public void remove() {
            if (!iterator)
                throw new UnsupportedOperationException();
            if (lastReturned == null)
                throw new IllegalStateException("Hashtable Enumerator");
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();

            synchronized(Hashtable.this) {
                Entry<?,?>[] tab = Hashtable.this.table;
                int index = (lastReturned.hash & 0x7FFFFFFF) % tab.length;

                @SuppressWarnings("unchecked")
                Entry<K,V> e = (Entry<K,V>)tab[index];
                for(Entry<K,V> prev = null; e != null; prev = e, e = e.next) {
                    if (e == lastReturned) {
                        modCount++;
                        expectedModCount++;
                        if (prev == null)
                            tab[index] = e.next;
                        else
                            prev.next = e.next;
                        count--;
                        lastReturned = null;
                        return;
                    }
                }
                throw new ConcurrentModificationException();
            }
        }
    }

}
