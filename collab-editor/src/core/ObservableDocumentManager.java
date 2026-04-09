package core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObservableDocumentManager implements DocumentManager {

    private final DocumentManager delegate;
    private final CopyOnWriteArrayList<DocumentChangeListener> listeners;

    public ObservableDocumentManager(DocumentManager delegate) {
        this.delegate  = delegate;
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public void addListener(DocumentChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DocumentChangeListener listener) {
        listeners.remove(listener);
    }



    @Override
    public void addLine(int index, String text) {
        delegate.addLine(index, text);
        fireChange("ADDL " + index + " " + text);
    }

    @Override
    public void removeLine(int index) {
        delegate.removeLine(index);
        fireChange("RMVL " + index);
    }

    @Override
    public void modifyLine(int index, String text) {
        delegate.modifyLine(index, text);
        fireChange("MDFL " + index + " " + text);
    }



    @Override
    public String getLine(int index) {
        return delegate.getLine(index);
    }

    @Override
    public List<String> getDocument() {
        return delegate.getDocument();
    }

    @Override
    public int size() {
        return delegate.size();
    }



    private void fireChange(String changeMessage) {
        for (DocumentChangeListener listener : listeners) {
            try {
                listener.onDocumentChanged(changeMessage);
            } catch (Exception e) {

                System.err.println("[Observable] Listener error: " + e.getMessage());
            }
        }
    }
}
