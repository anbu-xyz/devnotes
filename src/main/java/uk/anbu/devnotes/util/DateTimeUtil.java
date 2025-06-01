package uk.anbu.devnotes.util;

import java.time.Duration;

public class DateTimeUtil {

    public static String toHumanReadable(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days >= 365 * 2) {
            long years = days / 365;
            return years + " years ago";
        }

        if (days > 365) {
            long months = days / 30;
            return months + " months ago";
        }

        if (days >= 28 && days < 60) {
            return "a month ago";
        }

        if (days >= 60) {
            long months = days / 30;
            return months + " months ago";
        }

        if (days >= 14) {
            long weeks = days / 7;
            return weeks + " weeks ago";
        }

        if (days >= 7) {
            return "a week ago";
        }

        if (days > 0) {
            if (days == 1) {
                return "yesterday";
            }
            return days + " days ago";
        }

        if (hours > 0) {
            if (hours == 1) {
                return "an hour ago";
            }
            return hours + " hours ago";
        }

        if (minutes > 45) {
            return "about an hour ago";
        }

        if (minutes > 30) {
            return "about 30 mins ago";
        }

        if (minutes > 0) {
            if (minutes == 1) {
                return "1 minute ago";
            }
            return minutes + " minutes ago";
        }

        return "just now";
    }

}
