package protocol;

public final class ProtocolConstants {

    private ProtocolConstants() {}


    public static final String LINE = "LINE";
    public static final String GETL = "GETL";
    public static final String GETD = "GETD";
    public static final String MDFL = "MDFL";
    public static final String RMVL = "RMVL";
    public static final String ADDL = "ADDL";
    public static final String DONE = "DONE";
    public static final String ERRL = "ERRL";


    public static String line(int i, String text) { return LINE + " " + i + " " + text; }
    public static String done()                    { return DONE; }
    public static String error(String msg)         { return ERRL + " " + msg; }
    public static String error(int i, String msg)  { return ERRL + " " + i + " " + msg; }
}
