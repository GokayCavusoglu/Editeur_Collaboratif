package core;

public class DocumentIndexException extends RuntimeException {

    private final int requestedIndex;
    private final int documentSize;

    public DocumentIndexException(int requestedIndex, int documentSize) {
        super(String.format(
            "Line %d out of range [1, %d]", requestedIndex, documentSize));
        this.requestedIndex = requestedIndex;
        this.documentSize = documentSize;
    }

    public int getRequestedIndex() { return requestedIndex; }
    public int getDocumentSize()   { return documentSize; }
}
