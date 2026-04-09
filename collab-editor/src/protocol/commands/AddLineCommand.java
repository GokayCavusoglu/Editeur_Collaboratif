package protocol.commands;

import core.DocumentManager;
import protocol.Command;
import protocol.ProtocolConstants;

public class AddLineCommand implements Command {

    private final int index;
    private final String text;

    public AddLineCommand(int index, String text) {
        this.index = index;
        this.text  = text;
    }

    @Override
    public String execute(DocumentManager manager) {
        manager.addLine(index, text);
        return ProtocolConstants.line(index, text);
    }

    public int    getIndex() { return index; }
    public String getText()  { return text; }

    @Override
    public String toString() {
        return "AddLineCommand{index=" + index + ", text='" + text + "'}";
    }
}
