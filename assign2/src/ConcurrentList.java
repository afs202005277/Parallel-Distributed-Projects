import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentList<E> implements List<E>, Comparator<E> {
    private final List<E> list = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public E get(int index) {
        lock.lock();
        try {
            return list.get(index);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E set(int index, E element) {
        lock.lock();
        try {
            return list.set(index, element);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void add(int index, E element) {
        lock.lock();
        try {
            list.add(index, element);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public E remove(int index) {
        lock.lock();
        try {
            return list.remove(index);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int indexOf(Object o) {
        lock.lock();
        try {
            return list.indexOf(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        lock.lock();
        try {
            return list.lastIndexOf(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ListIterator<E> listIterator() {
        lock.lock();
        try {
            return list.listIterator();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        lock.lock();
        try {
            return list.listIterator(index);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        lock.lock();
        try {
            return list.subList(fromIndex, toIndex);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return list.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        lock.lock();
        try {
            return list.contains(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Iterator<E> iterator() {
        lock.lock();
        try {
            return list.iterator();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Object[] toArray() {
        lock.lock();
        try {
            return list.toArray();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        lock.lock();
        try {
            return list.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean add(E e) {
        lock.lock();
        try {
            return list.add(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        lock.lock();
        try {
            return list.remove(o);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        lock.lock();
        try {
            return new HashSet<>(list).containsAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        lock.lock();
        try {
            return list.addAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        lock.lock();
        try {
            return list.addAll(index, c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        lock.lock();
        try {
            return list.removeAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        lock.lock();
        try {
            return list.retainAll(c);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            list.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int compare(E o1, E o2) {
        return 0;
    }
}
