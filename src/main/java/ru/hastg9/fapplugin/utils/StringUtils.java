package ru.hastg9.fapplugin.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class StringUtils {

    public static String formatDouble(double num) {
        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.getDefault());
        otherSymbols.setDecimalSeparator('.');

        DecimalFormat decimalFormat = new DecimalFormat("##0.00", otherSymbols);

        return decimalFormat.format(num);
    }

}
