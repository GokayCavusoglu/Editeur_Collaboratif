package protocol.commands;

import core.DocumentManager;
import protocol.Command;
import protocol.ProtocolConstants;

public class ModifyLineCommand implements Command {

    private final int index;
    private final String text;

    public ModifyLineCommand(int index, String text) {
        this.index = index;
        this.text  = text;
    }

    @Override
    public String execute(DocumentManager manager) {
        manager.modifyLine(index, text);
        return ProtocolConstants.line(index, text);
    }

    public int    getIndex() { return index; }
    public String getText()  { return text; }

    @Override
    public String toString() {
        return "ModifyLineCommand{index=" + index + ", text='" + text + "'}";
    }
}
