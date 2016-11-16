package ru.mail.park.main;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

public class Utils {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static boolean isArrayValid(String[] array, String... possibleValues) {
        if (array == null) {
            return true;
        }
        int possibleValuesInArray = 0;
        final List list = Arrays.asList(array);
        for (String s : possibleValues) {
            if (list.contains(s)) {
                possibleValuesInArray++;
            }
        }
        return array.length == possibleValuesInArray;
    }

    public static String getFieldVote(int vote) {
        String field = null;
        if (vote == 1) {
            field = "likes";
        } else {
            if (vote == -1) {
                field = "dislikes";
            }
        }
        return field;
    }
}
