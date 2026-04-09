package core;

import java.util.List;

public interface DocumentManager {


    void addLine(int index, String text);


    void removeLine(int index);


    void modifyLine(int index, String text);


    String getLine(int index);


    List<String> getDocument();


    int size();
}
