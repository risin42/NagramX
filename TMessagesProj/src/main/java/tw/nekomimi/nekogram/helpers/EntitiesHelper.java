package tw.nekomimi.nekogram.helpers;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.LinkifyPort;
import org.telegram.messenger.MediaDataController;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class EntitiesHelper {
    // Table pattern: matches GFM table with header row, separator row, and data rows
    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile(
            "^([ \\t]*\\|?.*\\|.*\\|?[ \\t]*\\n)([ \\t]*\\|?[-| :]+\\|?[ \\t]*\\n)((?:[ \\t]*\\|?.*\\|.*\\|?[ \\t]*\\n?)+)",
            Pattern.MULTILINE
    );

    private static final Pattern[] PATTERNS = new Pattern[]{
            Pattern.compile("^`{3}(.*?)[\\n\\r](.*?[\\n\\r]?)`{3}", Pattern.MULTILINE | Pattern.DOTALL), // pre
            Pattern.compile("^`{3}[\\n\\r]?(.*?)[\\n\\r]?`{3}", Pattern.MULTILINE | Pattern.DOTALL), // pre
            Pattern.compile("[`]{3}([^`]+)[`]{3}"), // pre
            Pattern.compile("[`]([^`]+?)[`]", Pattern.DOTALL), // code
            Pattern.compile("[*]{2}([^*\\n]+)[*]{2}"), // bold
            Pattern.compile("[_]{2}([^_\\n]+)[_]{2}"), // italic
            Pattern.compile("[~]{2}([^~\\n]+)[~]{2}"), // strike
            Pattern.compile("[|]{2}([^|\\n]+)[|]{2}"), // spoiler
            Pattern.compile("\\[([^]]+?)]\\(" + LinkifyPort.WEB_URL_REGEX + "\\)")}; // link

    public static CharSequence parseMarkdown(CharSequence text) {
        var message = new CharSequence[]{text};
        parseMarkdown(message, true);
        return message[0];
    }

    public static void parseMarkdown(CharSequence[] message, boolean allowStrike) {
        var spannable = message[0] instanceof Spannable ? (Spannable) message[0] : Spannable.Factory.getInstance().newSpannable(message[0]);
        for (int i = 0; i < PATTERNS.length; i++) {
            if (!allowStrike && i == 6) {
                continue;
            }
            var m = PATTERNS[i].matcher(spannable);
            var sources = new ArrayList<String>();
            var destinations = new ArrayList<CharSequence>();
            find:
            while (m.find()) {
                var start = m.start();
                var end = m.end();
                var length = i < 3 ? 3 : i > 3 && i != 8 ? 2 : 1;
                var textStyleSpans = spannable.getSpans(start, end, TextStyleSpan.class);
                for (var textStyleSpan : textStyleSpans) {
                    if (!textStyleSpan.isMono()) {
                        continue;
                    }
                    int spanStart = spannable.getSpanStart(textStyleSpan);
                    int spanEnd = spannable.getSpanEnd(textStyleSpan);
                    if (spanStart < start + length || spanEnd > end - length) {
                        continue find;
                    }
                }
                var codeHighlightingSpans = spannable.getSpans(start, end, CodeHighlighting.Span.class);
                for (var codeHighlightingSpan : codeHighlightingSpans) {
                    int spanStart = spannable.getSpanStart(codeHighlightingSpan);
                    int spanEnd = spannable.getSpanEnd(codeHighlightingSpan);
                    if (spanStart < start + length || spanEnd > end - length) {
                        continue find;
                    }
                }

                var destination = new SpannableStringBuilder(spannable.subSequence(m.start(i == 0 ? 2 : 1), m.end(i == 0 ? 2 : 1)));
                if (destination.length() > 0) {
                    if (i == 0) {
                        if (destination.charAt(destination.length() - 1) == '\n') {
                            destination = (SpannableStringBuilder) destination.subSequence(0, destination.length() - 1);
                        }
                        destination.setSpan(new CodeHighlighting.Span(true, 0, null, m.group(1), destination.toString()), 0, destination.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (i < 8) {
                        var run = new TextStyleSpan.TextStyleRun();
                        switch (i) {
                            case 1:
                            case 2:
                            case 3:
                                run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
                                break;
                            case 4:
                                run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
                                break;
                            case 5:
                                run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
                                break;
                            case 6:
                                run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
                                break;
                            case 7:
                                run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                                break;
                        }
                        MediaDataController.addStyleToText(new TextStyleSpan(run), 0, destination.length(), destination, true);
                    } else {
                        destination.setSpan(new URLSpanReplacement(m.group(2)), 0, destination.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                sources.add(m.group(0));
                destinations.add(destination);
            }
            for (int j = 0; j < sources.size(); j++) {
                spannable = (Spannable) TextUtils.replace(spannable, new String[]{sources.get(j)}, new CharSequence[]{destinations.get(j)});
            }
        }

        // Table parsing - independent from PATTERNS[] (which doesn't support multi-line)
        spannable = (Spannable) parseTables(spannable);

        message[0] = spannable;
    }

    // Parse GFM tables and replace with TableSpan using placeholder char
    public static CharSequence parseTables(CharSequence text) {
        if (text == null) return text;
        // Collect positions manually first
        var positions = new ArrayList<int[]>();
        var m = TABLE_BLOCK_PATTERN.matcher(text);
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
        }
        
        if (positions.isEmpty()) {
            return text;
        }
        
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        for (int i = positions.size() - 1; i >= 0; i--) {
            int start = positions.get(i)[0];
            int end = positions.get(i)[1];
            String originalMarkdown = builder.subSequence(start, end).toString();
            String[][] parsed = parseTableRows(originalMarkdown);
            if (parsed == null) {
                continue;
            }

            // Replace entire table block with zero-width space
            boolean hasTrailingNewline = originalMarkdown.endsWith("\n");
            builder.replace(start, end, hasTrailingNewline ? "\u200B\n" : "\u200B");
            TableSpan span = new TableSpan(parsed, originalMarkdown);
            builder.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    // Parse table markdown into 2D String array
    static String[][] parseTableRows(String tableBlock) {
        var lines = tableBlock.split("\\n");
        if (lines.length < 2) return null;

        var validRows = new java.util.ArrayList<String[]>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            
            // Skip the markdown separator row which usually appears at index 1
            // e.g. |---|---| or |:---:|----:|
            if (i == 1 && line.matches("\\|?[-| :]+\\|?")) {
                continue;
            }

            if (line.startsWith("|")) line = line.substring(1);
            if (line.endsWith("|")) line = line.substring(0, line.length() - 1);
            String[] cells = line.split("\\|");
            // Trim each cell
            for (int j = 0; j < cells.length; j++) {
                cells[j] = cells[j].trim();
            }
            validRows.add(cells);
        }
        
        String[][] result = new String[validRows.size()][];
        for(int i = 0; i < validRows.size(); i++) {
            result[i] = validRows.get(i);
        }
        return result;
    }

}
