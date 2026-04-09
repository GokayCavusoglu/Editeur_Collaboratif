package core;

@FunctionalInterface
public interface DocumentChangeListener {


    void onDocumentChanged(String changeMessage);
}
