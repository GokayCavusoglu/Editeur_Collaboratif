package protocol.commands;

import core.DocumentManager;
import protocol.Command;
import protocol.ProtocolConstants;

public class RemoveLineCommand implements Command {

    private final int index;

    public RemoveLineCommand(int index) {
        this.index = index;
    }

    @Override
    public String execute(DocumentManager manager) {
        manager.removeLine(index);
        return ProtocolConstants.done();
    }

    public int getIndex() { return index; }

    @Override
    public String toString() {
        return "RemoveLineCommand{index=" + index + "}";
    }
}
