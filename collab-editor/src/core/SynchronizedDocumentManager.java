package core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SynchronizedDocumentManager implements DocumentManager {

    private final List<String> lines;
    private final ReadWriteLock rwLock;

    public SynchronizedDocumentManager() {
        this.lines  = new ArrayList<>();
        this.rwLock = new ReentrantReadWriteLock();
    }



    @Override
    public void addLine(int index, String text) {
        rwLock.writeLock().lock();
        try {
            if (index < 1)
                throw new DocumentIndexException(index, lines.size());

            int pos = Math.min(index, lines.size() + 1);
            lines.add(pos - 1, text);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void removeLine(int index) {
        rwLock.writeLock().lock();
        try {
            validateIndex(index);
            lines.remove(index - 1);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void modifyLine(int index, String text) {
        rwLock.writeLock().lock();
        try {
            validateIndex(index);
            lines.set(index - 1, text);
        } finally {
            rwLock.writeLock().unlock();
        }
    }



    @Override
    public String getLine(int index) {
        rwLock.readLock().lock();
        try {
            validateIndex(index);
            return lines.get(index - 1);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public List<String> getDocument() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(lines);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        rwLock.readLock().lock();
        try {
            return lines.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }



    private void validateIndex(int index) {
        if (index < 1 || index > lines.size())
            throw new DocumentIndexException(index, lines.size());
    }
}
