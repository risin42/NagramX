package tw.nekomimi.nekogram.translate;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Bin
 */
public class TransUtils {

    public static String encodeURIComponent(String str) {
        if (str == null) return null;

        byte[] bytes = str.getBytes(Charset.defaultCharset());
        StringBuilder builder = new StringBuilder(bytes.length);

        for (byte c : bytes) {
            String HEX = "0123456789ABCDEF";
            if (c >= 'a' ? c <= 'z' || c == '~' :
                    c >= 'A' ? c <= 'Z' || c == '_' :
                            c >= '0' ? c <= '9' : c == '-' || c == '.')
                builder.append((char) c);
            else
                builder.append('%')
                        .append(HEX.charAt(c >> 4 & 0xf))
                        .append(HEX.charAt(c & 0xf));
        }

        return builder.toString();
    }

}
