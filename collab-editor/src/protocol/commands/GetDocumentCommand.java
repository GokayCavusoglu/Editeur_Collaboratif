package protocol.commands;

import core.DocumentManager;
import protocol.Command;
import protocol.ProtocolConstants;

import java.util.List;

public class GetDocumentCommand implements Command {

    @Override
    public String execute(DocumentManager manager) {
        List<String> lines = manager.getDocument();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(ProtocolConstants.line(i + 1, lines.get(i))).append('\n');
        }
        sb.append(ProtocolConstants.done());
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GetDocumentCommand{}";
    }
}
