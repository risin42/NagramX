package tw.nekomimi.nekogram.utils;


import org.telegram.messenger.MessageObject;

public class TelegramUtil {

    public static String getFileNameWithoutEx(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length()))) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }

    // 消息是否为文件
    public static boolean messageObjectIsFile(int type, MessageObject messageObject) {
        boolean cansave = (type == 4 || type == 5 || type == 6 || type == 10);
        boolean downloading = messageObject.loadedFileSize > 0;

        //图片的问题
        if (type == 4 && messageObject.getDocument() == null) {
            return false;
        }
        return cansave || downloading;
    }

}