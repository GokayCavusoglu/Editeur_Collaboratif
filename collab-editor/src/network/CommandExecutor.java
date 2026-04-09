package network;

import core.DocumentIndexException;
import core.DocumentManager;
import protocol.Command;
import protocol.CommandParser;
import protocol.InvalidCommandException;
import protocol.ProtocolConstants;

public class CommandExecutor {

    private final DocumentManager documentManager;
    private final CommandParser parser;

    public CommandExecutor(DocumentManager documentManager) {
        this.documentManager = documentManager;
        this.parser = new CommandParser();
    }


    public String process(String rawMessage) {
        try {
            Command command = parser.parse(rawMessage);
            return command.execute(documentManager);
        } catch (InvalidCommandException e) {
            return ProtocolConstants.error(e.getMessage());
        } catch (DocumentIndexException e) {
            return ProtocolConstants.error(e.getRequestedIndex(), e.getMessage());
        } catch (Exception e) {
            return ProtocolConstants.error("Internal error: " + e.getMessage());
        }
    }
}
