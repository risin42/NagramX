package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class TableSpan extends ReplacementSpan {

    private static final int CELL_PADDING = 6; // dp
    private static final int BORDER_WIDTH = 1; // dp
    private static final int MIN_COLUMN_WIDTH = 40; // dp

    private final String[][] rows;
    private final int maxWidth;
    private int[] columnWidths;

    // Lazy-measured dimensions
    private int mTableWidth = 0;
    private int mTableHeight = 0;
    private boolean mMeasured = false;
    private int mMeasuredDisplayWidth = -1;
    private float mMeasuredTextSize = -1f;

    public TableSpan(String[][] rows, int maxWidth) {
        this.rows = rows;
        this.maxWidth = maxWidth;
    }

    private int[] rowHeights;
    private StaticLayout[][] layouts;
    private TextPaint mTextPaint;
    private Paint borderPaint;
    private Paint bgPaint;
    private Paint headerPaint;

    private void measureIfNeeded(Paint paint) {
        int displayWidth = maxWidth > 0 ? maxWidth : AndroidUtilities.displaySize.x;
        float textSize = paint.getTextSize();
        if (mMeasured && mMeasuredDisplayWidth == displayWidth && mMeasuredTextSize == textSize) {
            return;
        }
        mMeasuredDisplayWidth = displayWidth;
        mMeasuredTextSize = textSize;
        mTableWidth = 0;
        mTableHeight = 0;

        if (rows == null || rows.length == 0 || rows[0] == null || rows[0].length == 0) {
            mMeasured = true;
            return;
        }

        int colCount = rows[0].length;
        columnWidths = new int[colCount];
        int minColumnPx = dp(MIN_COLUMN_WIDTH);

        mTextPaint = new TextPaint(paint);

        // 1. Calculate natural column widths based on longest string
        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            mTextPaint.setFakeBoldText(r == 0);
            for (int c = 0; c < colCount; c++) {
                String cell = c < row.length ? row[c] : "";
                int w = (int) Math.ceil(mTextPaint.measureText(cell));
                if (w > columnWidths[c]) {
                    columnWidths[c] = w;
                }
            }
        }

        // 2. Adjust for min width and sum up natural total
        int totalContentWidth = 0;
        for (int c = 0; c < colCount; c++) {
            columnWidths[c] = Math.max(minColumnPx, columnWidths[c]);
            totalContentWidth += columnWidths[c];
        }

        int maxTableWidth = maxWidth > 0 ? Math.max(1, mMeasuredDisplayWidth) : Math.max(1, mMeasuredDisplayWidth - dp(100));
        int decorationsWidth = dp(CELL_PADDING) * 2 * colCount + dp(BORDER_WIDTH) * (colCount + 1);
        int availableSpace = Math.max(1, maxTableWidth - decorationsWidth);
        int minShrinkPx = Math.max(1, minColumnPx / 2);

        // 3. If total content exceeds available space, shrink proportionally
        if (totalContentWidth > availableSpace) {
            float ratio = (float) availableSpace / totalContentWidth;
            for (int c = 0; c < colCount; c++) {
                columnWidths[c] = (int) (columnWidths[c] * ratio);
                columnWidths[c] = Math.max(minShrinkPx, columnWidths[c]);
            }
        }

        int adjustedContentWidth = 0;
        for (int c = 0; c < colCount; c++) {
            adjustedContentWidth += columnWidths[c];
        }
        if (adjustedContentWidth > availableSpace) {
            float ratio = (float) availableSpace / adjustedContentWidth;
            adjustedContentWidth = 0;
            for (int c = 0; c < colCount; c++) {
                columnWidths[c] = Math.max(1, (int) (columnWidths[c] * ratio));
                adjustedContentWidth += columnWidths[c];
            }
        }

        // 4. Calculate actual table width
        mTableWidth = Math.min(maxTableWidth, decorationsWidth + adjustedContentWidth);

        mTableHeight = dp(BORDER_WIDTH) * (rows.length + 1);
        rowHeights = new int[rows.length];
        layouts = new StaticLayout[rows.length][colCount];

        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            int maxH = 0;
            mTextPaint.setFakeBoldText(r == 0);

            for (int c = 0; c < colCount; c++) {
                String cell = c < row.length ? row[c] : "";
                int w = Math.max(1, columnWidths[c]);
                StaticLayout layout = new StaticLayout(cell, mTextPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                layouts[r][c] = layout;
                maxH = Math.max(maxH, layout.getHeight());
            }
            rowHeights[r] = maxH + dp(CELL_PADDING) * 2;
            mTableHeight += rowHeights[r];
        }

        mMeasured = true;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        measureIfNeeded(paint);
        if (fm != null) {
            fm.ascent = -mTableHeight;
            fm.top = -mTableHeight;
            fm.descent = 0;
            fm.bottom = 0;
        }
        return mTableWidth;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x,
                     int top, int y, int bottom, @NonNull Paint paint) {
        measureIfNeeded(paint);
        drawTableContent(canvas, x, top, paint);
    }

    protected void drawTableContent(Canvas canvas, float x, int top, Paint paint) {
        if (rows == null || rows.length == 0 || rows[0] == null || rows[0].length == 0) {
            return;
        }
        int rowCount = rows.length;
        int colCount = rows[0].length;
        int borderWidth = dp(BORDER_WIDTH);
        int padding = dp(CELL_PADDING);

        int textColor = paint.getColor();
        int accentColor = paint instanceof TextPaint ? ((TextPaint) paint).linkColor : textColor;

        int baseAlpha = Color.alpha(accentColor);
        int red = Color.red(accentColor);
        int green = Color.green(accentColor);
        int blue = Color.blue(accentColor);

        int backgroundColor = Color.argb((int) (baseAlpha * 0.08f), red, green, blue);
        int headerColor = Color.argb((int) (baseAlpha * 0.15f), red, green, blue);
        int borderColor = Color.argb((int) (baseAlpha * 0.3f), red, green, blue);

        if (borderPaint == null) {
            borderPaint = new Paint(paint);
            borderPaint.setStyle(Paint.Style.STROKE);
        }
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setColor(borderColor);

        if (bgPaint == null) {
            bgPaint = new Paint(paint);
            bgPaint.setStyle(Paint.Style.FILL);
        }
        bgPaint.setColor(backgroundColor);

        if (headerPaint == null) {
            headerPaint = new Paint(paint);
            headerPaint.setStyle(Paint.Style.FILL);
        }
        headerPaint.setColor(headerColor);

        canvas.drawRect(x, top, x + mTableWidth, top + mTableHeight, bgPaint);
        canvas.drawRect(x, top, x + mTableWidth, top + rowHeights[0] + borderWidth, headerPaint);

        canvas.drawRect(x, top, x + mTableWidth, top + mTableHeight, borderPaint);

        int currentY = top;
        for (int r = 0; r < rowCount; r++) {
            currentY += rowHeights[r] + borderWidth;
            if (r < rowCount - 1) {
                canvas.drawLine(x, currentY, x + mTableWidth, currentY, borderPaint);
            }
        }

        int cumulativeWidth = 0;
        for (int c = 0; c < colCount - 1; c++) {
            cumulativeWidth += columnWidths[c] + padding * 2 + borderWidth;
            float lineX = x + cumulativeWidth;
            canvas.drawLine(lineX, top, lineX, top + mTableHeight, borderPaint);
        }

        if (mTextPaint != null) {
            mTextPaint.setColor(textColor);
            mTextPaint.setStyle(Paint.Style.FILL);
        }

        float cellTop = top + borderWidth;
        for (int r = 0; r < rowCount; r++) {
            float cellLeft = x + borderWidth;
            if (mTextPaint != null) {
                mTextPaint.setFakeBoldText(r == 0);
            }

            for (int c = 0; c < colCount; c++) {
                int cellWidth = columnWidths[c];
                StaticLayout layout = layouts[r][c];

                if (layout != null) {
                    canvas.save();
                    float textX = cellLeft + padding;
                    float textY = cellTop + padding;
                    canvas.translate(textX, textY);
                    layout.draw(canvas);
                    canvas.restore();
                }

                cellLeft += cellWidth + padding * 2 + borderWidth;
            }
            cellTop += rowHeights[r] + borderWidth;
        }
    }

}
