package protocol;

import protocol.commands.*;

public class CommandParser {


    public Command parse(String raw) throws InvalidCommandException {
        if (raw == null || raw.isBlank())
            throw new InvalidCommandException("Empty command");

        String trimmed = raw.trim();
        String keyword = extractKeyword(trimmed);

        switch (keyword) {
            case ProtocolConstants.ADDL: return parseAddLine(trimmed);
            case ProtocolConstants.RMVL: return parseRemoveLine(trimmed);
            case ProtocolConstants.MDFL: return parseModifyLine(trimmed);
            case ProtocolConstants.GETD: return new GetDocumentCommand();
            case ProtocolConstants.GETL: return parseGetLine(trimmed);
            default:
                throw new InvalidCommandException("Unknown keyword: " + keyword);
        }
    }




    private Command parseAddLine(String msg) throws InvalidCommandException {
        String[] parts = splitArgs(msg, 3);
        int index  = parseIndex(parts[1]);
        String text = parts.length > 2 ? parts[2] : "";
        return new AddLineCommand(index, text);
    }


    private Command parseRemoveLine(String msg) throws InvalidCommandException {
        String[] parts = splitArgs(msg, 2);
        int index = parseIndex(parts[1]);
        return new RemoveLineCommand(index);
    }


    private Command parseModifyLine(String msg) throws InvalidCommandException {
        String[] parts = splitArgs(msg, 3);
        int index  = parseIndex(parts[1]);
        String text = parts.length > 2 ? parts[2] : "";
        return new ModifyLineCommand(index, text);
    }


    private Command parseGetLine(String msg) throws InvalidCommandException {
        String[] parts = splitArgs(msg, 2);
        int index = parseIndex(parts[1]);
        return new GetLineCommand(index);
    }



    private String extractKeyword(String msg) {
        int space = msg.indexOf(' ');
        return space < 0 ? msg : msg.substring(0, space);
    }


    private String[] splitArgs(String msg, int limit) throws InvalidCommandException {
        String[] parts = msg.split("\\s+", limit);
        if (parts.length < 2 && limit >= 2)
            throw new InvalidCommandException("Missing arguments: " + msg);
        return parts;
    }

    private int parseIndex(String token) throws InvalidCommandException {
        try {
            int i = Integer.parseInt(token);
            if (i < 1) throw new InvalidCommandException("Index must be >= 1, got " + i);
            return i;
        } catch (NumberFormatException e) {
            throw new InvalidCommandException("Invalid index: " + token);
        }
    }
}
