package protocol;

import core.DocumentManager;

public interface Command {


    String execute(DocumentManager manager);
}
