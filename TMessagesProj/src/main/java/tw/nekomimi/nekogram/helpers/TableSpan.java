package tw.nekomimi.nekogram.helpers;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import org.telegram.messenger.AndroidUtilities;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TableSpan extends ReplacementSpan {

    private static final int CELL_PADDING = 6; // dp
    private static final int BORDER_WIDTH = 1; // dp
    private static final int MAX_COLUMN_WIDTH = 64; // dp
    private static final int MIN_COLUMN_WIDTH = 40; // dp
    private static final int UNSET = Integer.MIN_VALUE;

    private final String[][] rows;
    private final String originalMarkdown;
    private int[] columnWidths;

    // Lazy-measured dimensions
    private int mTableWidth = 0;
    private int mTableHeight = 0;
    private boolean mMeasured = false;

    // Draw guard
    private int mFirstTop = UNSET;

    // Copy button rect for external hit-test
    @Nullable
    private RectF copyButtonRect;

    public TableSpan(String[][] rows, String originalMarkdown) {
        this.rows = rows;
        this.originalMarkdown = originalMarkdown;
    }

    @Nullable
    public RectF getCopyButtonRect() {
        return copyButtonRect;
    }

    public String getOriginalText() {
        return originalMarkdown;
    }

    public void resetDrawState() {
        mFirstTop = UNSET;
        mMeasured = false;
    }


    private int[] rowHeights;
    private StaticLayout[][] layouts;

    private TextPaint mTextPaint;

    private void measureIfNeeded(Paint paint) {
        if (mMeasured) {
            return;
        }

        int colCount = rows[0].length;
        columnWidths = new int[colCount];
        int maxTableWidth = AndroidUtilities.displaySize.x - dp(100);
        int availableSpace = maxTableWidth - dp(CELL_PADDING) * 2 * colCount - dp(BORDER_WIDTH) * (colCount + 1);

        int avgWidth = availableSpace / colCount;
        for (int i = 0; i < colCount; i++) {
            columnWidths[i] = avgWidth;
        }

        mTableWidth = maxTableWidth;
        mTableHeight = dp(BORDER_WIDTH) * (rows.length + 1);
        rowHeights = new int[rows.length];
        layouts = new StaticLayout[rows.length][colCount];

        mTextPaint = new TextPaint(paint);

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
    private int dp(float dp) {
        return (int) (dp * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        android.util.Log.d("TableSpan", "getSize called: start=" + start + " end=" + end + " text.length=" + text.length());
        measureIfNeeded(paint);
        android.util.Log.d("TableSpan", "getSize returning: width=" + mTableWidth + " height=" + mTableHeight);
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
        android.util.Log.d("TableSpan", "draw called: x=" + x + " top=" + top + " y=" + y + " bottom=" + bottom + " mTableWidth=" + mTableWidth + " mTableHeight=" + mTableHeight + " text.length=" + text.length());
        // Only draw on first line (with placeholder char, this is always called once)
        if (mFirstTop == UNSET) {
            mFirstTop = top;
            android.util.Log.d("TableSpan", "First top set to: " + top);
        }
        if (top != mFirstTop) {
            android.util.Log.d("TableSpan", "Skipping draw - top=" + top + " mFirstTop=" + mFirstTop);
            return;
        }

        measureIfNeeded(paint);
        android.util.Log.d("TableSpan", "Drawing table at x=" + x + " top=" + top + " width=" + mTableWidth + " height=" + mTableHeight);
        drawTableContent(canvas, x, top, paint);
        updateCopyButtonRect(x, top, bottom);
    }


    protected void drawTableContent(Canvas canvas, float x, int top, Paint paint) {
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

        int backgroundColor = Color.argb((int)(baseAlpha * 0.08f), red, green, blue);
        int headerColor = Color.argb((int)(baseAlpha * 0.15f), red, green, blue);
        int borderColor = Color.argb((int)(baseAlpha * 0.3f), red, green, blue);

        Paint borderPaint = new Paint(paint);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);
        borderPaint.setColor(borderColor);

        Paint bgPaint = new Paint(paint);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(backgroundColor);

        Paint headerPaint = new Paint(paint);
        headerPaint.setStyle(Paint.Style.FILL);
        headerPaint.setColor(headerColor);

        canvas.drawRect(x, top, x + mTableWidth, top + mTableHeight, bgPaint);
        if (rowCount > 0) {
            canvas.drawRect(x, top, x + mTableWidth, top + rowHeights[0] + borderWidth, headerPaint);
        }

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
    private String fitText(Paint paint, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int count = paint.breakText(text, true, Math.max(0, maxWidth - paint.measureText(ellipsis)), null);
        if (count <= 0) {
            return ellipsis;
        }
        return text.substring(0, count) + ellipsis;
    }

    // Will be implemented in Step 4
    protected void updateCopyButtonRect(float x, int top, int bottom) {
        copyButtonRect = null;
    }
}
