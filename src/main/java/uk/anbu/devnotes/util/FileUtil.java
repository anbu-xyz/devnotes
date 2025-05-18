package uk.anbu.devnotes.util;

import java.util.ArrayList;

public class FileUtil {
    public static String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }

    public static String cleanDirectoryName(String parentDirectoryName) {
        // remove leading slashes
        parentDirectoryName = parentDirectoryName.replaceAll("^/+", "");
        // remove trailing slashes
        parentDirectoryName = parentDirectoryName.replaceAll("/+$", "");

        var split = parentDirectoryName.split("/");
        var reversedSplit = new ArrayList<String>();
        for (int i = split.length - 1; i >= 0; i--) {
            reversedSplit.add(split[i]);
        }
        var doubleDotsRemoved = new ArrayList<String>();
        int skipCount = 0;
        for (int i = 0; i < reversedSplit.size(); i++) {
            var entry = reversedSplit.get(i);
            if (entry.equals("..")) {
                skipCount++;
            } else if (entry.equals(".")) {
                // do nothing
            } else if (skipCount > 0) {
                skipCount--;
            }
            else {
                doubleDotsRemoved.add(entry);
            }
        }
        var cleanedDirectoryName = new StringBuilder();
        for(var i = doubleDotsRemoved.size() - 1; i >= 0; i--) {
            cleanedDirectoryName.append(doubleDotsRemoved.get(i));
            if (i != 0) {
                cleanedDirectoryName.append("/");
            }
        }
        parentDirectoryName = cleanedDirectoryName.toString();
        return parentDirectoryName;
    }

}
