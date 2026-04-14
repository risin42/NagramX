import re

with open("TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/TableSpan.java", "r") as f:
    text = f.read()

# Make imports
if "import android.text.StaticLayout;" not in text:
    text = text.replace("import android.text.Layout;", "import android.text.Layout;\nimport android.text.StaticLayout;")
if "org.telegram.messenger.AndroidUtilities" not in text:
    text = text.replace("import android.text.Layout;", "import org.telegram.messenger.AndroidUtilities;\nimport android.text.Layout;")

new_measure = """
    private int[] rowHeights;
    private StaticLayout[][] layouts;

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

        TextPaint textPaint;
        if (paint instanceof TextPaint) {
            textPaint = (TextPaint) paint;
        } else {
            textPaint = new TextPaint(paint);
        }

        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            int maxH = 0;
            textPaint.setFakeBoldText(r == 0);

            for (int c = 0; c < colCount; c++) {
                String cell = c < row.length ? row[c] : "";
                int w = Math.max(1, columnWidths[c]);
                StaticLayout layout = new StaticLayout(cell, textPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                layouts[r][c] = layout;
                maxH = Math.max(maxH, layout.getHeight());
            }
            rowHeights[r] = maxH + dp(CELL_PADDING) * 2;
            mTableHeight += rowHeights[r];
        }

        mMeasured = true;
    }
"""

text = re.sub(r'    private void measureIfNeeded\(Paint paint\) \{.*?(?=    private int dp\()', new_measure, text, flags=re.DOTALL | re.MULTILINE)

new_draw = """
    protected void drawTableContent(Canvas canvas, float x, int top, Paint paint) {
        int rowCount = rows.length;
        int colCount = rows[0].length;
        int borderWidth = dp(BORDER_WIDTH);
        int padding = dp(CELL_PADDING);

        int textColor = paint.getColor();
        boolean darkText = Color.red(textColor) + Color.green(textColor) + Color.blue(textColor) < 384;
        int backgroundColor = darkText ? Color.argb(24, 255, 255, 255) : Color.argb(18, 0, 0, 0);
        int headerColor = darkText ? Color.argb(36, 255, 255, 255) : Color.argb(28, 0, 0, 0);
        int borderColor = darkText ? Color.argb(160, 255, 255, 255) : Color.argb(120, 0, 0, 0);

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

        TextPaint textPaint = new TextPaint(paint);
        textPaint.setColor(textColor);
        textPaint.setStyle(Paint.Style.FILL);

        float cellTop = top + borderWidth;
        for (int r = 0; r < rowCount; r++) {
            float cellLeft = x + borderWidth;
            textPaint.setFakeBoldText(r == 0);
            
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
"""

text = re.sub(r'    protected void drawTableContent\(Canvas canvas, float x, int top, Paint paint\) \{.*?(?=    private String fitText\()', new_draw, text, flags=re.DOTALL | re.MULTILINE)

with open("TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/TableSpan.java", "w") as f:
    f.write(text)
