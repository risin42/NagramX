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
import java.util.Arrays;
import java.util.regex.Pattern;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

public class EntitiesHelper {
    public static final class TableSelectionSpan {
    }

    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile(
            "^([ \\t]*\\|?.*\\|.*\\|?[ \\t]*\\n)([ \\t]*\\|?[-| :]+\\|?[ \\t]*\\n)((?:[ \\t]*\\|?.*\\|.*\\|?[ \\t]*\\n?)+)",
            Pattern.MULTILINE  // Table pattern: matches GFM table with header row, separator row, and data rows
    );
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^[ \\t]*\\|?([ \\t]*:?-+:?[ \\t]*\\|[ \\t]*)*([ \\t]*:?-+:?[ \\t]*)\\|?[ \\t]*$");
    private static final Pattern TABLE_CELL_SPLIT_PATTERN = Pattern.compile("(?<!\\\\)\\|");

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
        message[0] = spannable;
    }

    public static CharSequence parseTables(CharSequence text, int maxWidth) {
        if (TextUtils.isEmpty(text) || maxWidth <= 0) {
            return text;
        }
        if (NaConfig.INSTANCE.getMarkdownParser().Int() != NekoConfig.MARKDOWN_PARSER_NEKO) {
            return text;
        }
        if (TextUtils.indexOf(text, '|') == -1 || TextUtils.indexOf(text, '\n') == -1) {
            return text;
        }

        var positions = findTablePositions(text);
        if (positions.isEmpty()) {
            return text;
        }

        Spannable originalSpannable = text instanceof Spannable ? (Spannable) text : null;
        if (originalSpannable != null) {
            TableSelectionSpan[] spans = originalSpannable.getSpans(0, originalSpannable.length(), TableSelectionSpan.class);
            for (TableSelectionSpan span : spans) {
                originalSpannable.removeSpan(span);
            }
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

            CharSequence placeholder = createTablePlaceholder(originalMarkdown);
            builder.replace(start, end, placeholder);
            TableSpan span = new TableSpan(parsed, maxWidth);
            builder.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (originalSpannable != null) {
                originalSpannable.setSpan(new TableSelectionSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private static CharSequence createTablePlaceholder(String originalMarkdown) {
        int length = originalMarkdown.length();
        char[] chars = new char[length];
        Arrays.fill(chars, '\u200B');
        if (originalMarkdown.charAt(length - 1) == '\n') {
            chars[length - 1] = '\n';
        }
        return new String(chars);
    }

    private static ArrayList<int[]> findTablePositions(CharSequence text) {
        var positions = new ArrayList<int[]>();
        var m = TABLE_BLOCK_PATTERN.matcher(text);
        while (m.find()) {
            if (!isInsideMonoOrCodeSpan(text, m.start(), m.end())) {
                positions.add(new int[]{m.start(), m.end()});
            }
        }
        return positions;
    }

    private static boolean isInsideMonoOrCodeSpan(CharSequence text, int start, int end) {
        if (!(text instanceof Spanned spanned)) {
            return false;
        }
        int effectiveEnd = end;
        if (effectiveEnd > start && text.charAt(effectiveEnd - 1) == '\n') {
            effectiveEnd--;
        }

        CodeHighlighting.Span[] codeSpans = spanned.getSpans(start, end, CodeHighlighting.Span.class);
        if (codeSpans != null) {
            for (CodeHighlighting.Span codeSpan : codeSpans) {
                int spanStart = spanned.getSpanStart(codeSpan);
                int spanEnd = spanned.getSpanEnd(codeSpan);
                if (spanStart <= start && spanEnd >= effectiveEnd) {
                    return true;
                }
            }
        }

        TextStyleSpan[] styleSpans = spanned.getSpans(start, end, TextStyleSpan.class);
        if (styleSpans != null) {
            for (TextStyleSpan span : styleSpans) {
                if (!span.isMono()) {
                    continue;
                }
                int spanStart = spanned.getSpanStart(span);
                int spanEnd = spanned.getSpanEnd(span);
                if (spanStart <= start && spanEnd >= effectiveEnd) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInsideTableSelectionSpan(CharSequence text, int offset) {
        return getTableSelectionRange(text, offset) != null;
    }

    public static int[] getTableSelectionRange(CharSequence text, int offset) {
        if (!(text instanceof Spanned spanned) || offset < 0 || offset > text.length()) {
            return null;
        }

        int queryOffset = offset;
        if (queryOffset >= text.length() && text.length() > 0) {
            queryOffset = text.length() - 1;
        }

        int queryStart = queryOffset;
        int queryEnd = Math.min(text.length(), queryOffset + 1);
        if (queryStart == queryEnd && queryStart > 0) {
            queryStart--;
        }

        EntitiesHelper.TableSelectionSpan[] spans = spanned.getSpans(queryStart, queryEnd, EntitiesHelper.TableSelectionSpan.class);
        if (spans == null || spans.length == 0) {
            return null;
        }

        int start = spanned.getSpanStart(spans[0]);
        int end = spanned.getSpanEnd(spans[0]);
        if (start < 0 || end <= start) {
            return null;
        }
        return new int[]{start, end};
    }

    // Parse table markdown into 2D String array
    private static String[][] parseTableRows(String tableBlock) {
        var lines = tableBlock.split("\\r?\\n");
        if (lines.length < 2) return null;

        var validRows = new ArrayList<String[]>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            if (i == 1 && TABLE_SEPARATOR_PATTERN.matcher(line).matches()) {
                continue;
            }

            if (line.startsWith("|")) {
                line = line.substring(1);
            }
            if (line.endsWith("|") && !line.endsWith("\\|")) {
                line = line.substring(0, line.length() - 1);
            }

            String[] cells = TABLE_CELL_SPLIT_PATTERN.split(line);

            for (int j = 0; j < cells.length; j++) {
                cells[j] = cells[j].replace("\\|", "|").trim();
            }
            validRows.add(cells);
        }

        String[][] result = new String[validRows.size()][];
        for (int i = 0; i < validRows.size(); i++) {
            result[i] = validRows.get(i);
        }
        return result;
    }
}
