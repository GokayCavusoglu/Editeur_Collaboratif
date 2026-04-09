package protocol.commands;

import core.DocumentManager;
import protocol.Command;
import protocol.ProtocolConstants;

public class GetLineCommand implements Command {

    private final int index;

    public GetLineCommand(int index) {
        this.index = index;
    }

    @Override
    public String execute(DocumentManager manager) {
        String text = manager.getLine(index);
        return ProtocolConstants.line(index, text);
    }

    public int getIndex() { return index; }

    @Override
    public String toString() {
        return "GetLineCommand{index=" + index + "}";
    }
}
